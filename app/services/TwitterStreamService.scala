package services

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.{Attributes, FanOutShape}
import akka.stream.scaladsl.FlexiRoute
import akka.stream.scaladsl.Source
import org.reactivestreams.Publisher
import play.api.Play.current
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator}
import play.api.libs.json.{JsArray, JsObject, JsValue}
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.streams.Streams
import play.api.libs.ws.{WS, WSAPI}
import play.api.{Configuration, Logger}
import play.extras.iteratees.{Encoding, JsonIteratees}

import scala.concurrent.ExecutionContext

/**
  * Created by carlos on 06/02/17.
  */
class TwitterStreamService @Inject()(
                                    ws: WSAPI,
                                    system: ActorSystem,
                                    executionContext: ExecutionContext,
                                    configuration: Configuration
                                    ) {

  /**
    * Defines the method for building the enumerator that streams the parsed tweets.
    *
    * @param consumerKey
    * @param requestToken
    * @param topics
    * @return
    */
  private def buildTwitterEnumerator(
                                    consumerKey: ConsumerKey,
                                    requestToken: RequestToken,
                                    topics: Seq[String]
                                    ): Enumerator[JsObject] = {
    // Creates a linked pair of iteratee and enumerator as a simple adapter in the pipeline
    val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
    val url = "https://stream.twitter.com/1.1/statuses/filter.json"
    implicit val ec = executionContext

    // Formats the topics you want to track
    val formattedTopics = topics
      .map(t => "#" + t)
      .mkString(",")

    WS
      .url(url)
      .sign(OAuthCalculator(consumerKey, requestToken))
      // Sends a POST request and fetches the stream the stream from Twitter.
      // This method expects to be fed a body as well as a consumer.
      .postAndRetrieveStream(
        Map("track" -> Seq(formattedTopics))
      ) { response =>
        Logger.info("Status: " + response.status)
        // Passes in the iteratee as a consumer. The stream will flow through this iteratee to the joined enumerator.
        iteratee
    }.map { _ =>
      Logger.info("Twitter stream closed")
    }

    // Transforms the stream by decoding and parsing it
    val jsonStream: Enumerator[JsObject] = enumerator &>
      Encoding.decode() &>
      Enumeratee.grouped(JsonIteratees.jsSimpleObject)

    // Returns the transformed stream as an enumerator
    jsonStream
  }


  private def enumeratorToSource[Out](enum: Enumerator[Out]): Source[Out, Unit] = {
    // Turns the enumerator into a Reactive Streams publisher
    val publisher: Publisher[Out] = Streams.enumeratorToPublisher(enum)
    // Turns the publisher into an Akka Streams source
    Source(publisher)
  }


  /**
    * Defines the stream function that you'll feed with the topics and their associated rates.
    *
    * @param topicsAndDigestRate
    * @return Enumerator[JsValue]   Specifies that you want to get an enumerator as a result to feed it
    *         into a WeSocket connection.
    */
  def stream(topicsAndDigestRate: Map[String, Int]): Enumerator[JsValue] = {

    import akka.stream.FanOutShape._

    /**
      * Defines the shape of the custom junction by extending FanOutShape. Because this is a fan-out junction,
      * you only describe the output ports (outlets) because there is only one input port.
      *
      * @param _init
      * @tparam A
      */
    class SplitByTopicShape[A <: JsObject](_init: Init[A] = Name[A]("SplitByTopic")) extends FanOutShape[A](_init) {
      protected override def construct(i: Init[A]) = new SplitByTopicShape(i)
      // Creates one output port per topic and keeps these ports in a map so that you can retrieve them by topic later
      val topicOutlets = topicsAndDigestRate.keys.map { topic =>
        topic -> newOutlet[A]("out-" + topic)
      }.toMap
    }

    /**
      * Defines the custom junction by extending FlexiRoute
      *
      * @tparam A
      */
    class SplitByTopic[A <: JsObject]
      extends FlexiRoute[A, SplitByTopicShape[A]](new SplitByTopicShape, Attributes.name("SplitByTopic")) {

      import FlexiRoute._

      /**
        * Defines the routing logic of the junction where you'll define how elements get routed
        *
        * @param p
        * @return
        */
      override def createRouteLogic(p: PortT) = new RouteLogic[A] {
        // Extracts the first topic out of a tweet. You'll split using only the first topic in this example.
        def extractFirstHashTag(tweet: JsObject) =
          (tweet \ "entities" \ "hashtags")
            .asOpt[JsArray]
            .flatMap { hashtags =>
              hashtags.value.headOption.map { hashtag =>
                (hashtag \ "text").as[String]
              }
            }

        override def initialState =
          // Specifies the demand condition that you want to use. In this case,
          // you trigger when any of the outward streams is ready to receive more elements.
          State[Any](DemandFromAny(p.topicOutlets.values.toSeq :_*)) {
            (ctx, _, element) =>
              // Uses the first hash of a tweet to route it to the appropriate port, ignoring tweets that don't match.
              extractFirstHashTag(element).foreach { topic =>
                p.topicOutlets.get(topic).foreach { port =>
                  ctx.emit(port)(element)
                }
              }
              SameState
          }

        override def initialCompletionHandling = eagerClose
      }
    }

    Enumerator.empty[JsValue]
    // TODO to be continued...
  }
}

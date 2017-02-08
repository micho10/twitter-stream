package services

import javafx.scene.control.TreeItem
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, Attributes, FanOutShape, Outlet}
import org.reactivestreams.Publisher
import play.api.Play.current
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.streams.Streams
import play.api.libs.ws.{WS, WSAPI}
import play.api.{Configuration, Logger, Play}
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
      val topicOutlets: Map[String, Outlet[A]] = topicsAndDigestRate.keys.map { topic =>
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
        def extractFirstHashTag(tweet: JsObject): Option[String] =
          (tweet \ "entities" \ "hashtags")
            .asOpt[JsArray]
            .flatMap { hashtags =>
              hashtags.value.headOption.map { hashtag =>
                (hashtag \ "text").as[String]
              }
            }

        override def initialState: State[Any] =
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

        override def initialCompletionHandling: CompletionHandling = eagerClose
      }
    }

    credentials.map { case (consumerKey, requestToken) =>

      // Creates a FlowMaterializer that you'll need to be able to run the graph flow
      implicit val fm = ActorMaterializer()(system)

      // Builds the enumerator source
      val enumerator = buildTwitterEnumerator(consumerKey, requestToken, topicsAndDigestRate.keys.toSeq)
      // Defines a sink that the data will flow to. Uses a sink tah will produce a Reactive Streams publisher,
      // which you'll later turn back into an enumerator.
      val sink = Sink.publisher[JsValue]
      // Creates a builder for a closed FlowGraph, passing in the sink as an output value that will be materialized
      // when the flow runs
      val graph = FlowGraph.closed(sink) { implicit builder => out =>
        // Adds the source to the graph
        val in = builder.add(enumeratorToSource(enumerator))
        // Adds the custom splitter to the graph
        val splitter = builder.add(new SplitByTopic[JsObject])
        // Adds the groupers to the graph, one for each topic. These will group the specified number of elements
        // together, depending on the rate of each topic.
        val groupers = topicsAndDigestRate.map { case (topic, rate) =>
            topic -> builder.add(Flow[JsObject].grouped(rate))
        }
        // Adds the taggers to the graph, one for each topic. These will take the grouped tweets and build one
        // JSON object out of them, tagging it with the topic.
        val taggers = topicsAndDigestRate.map { case (topic, _) =>
          topic -> {
            val t = Flow[Seq[JsObject]].map { tweets =>
              Json.obj("topic" -> topic, "tweets" -> tweets)
            }
            builder.add(t)
          }
        }
        // Adds a merger to the graph to merge all streams back together
        val merger = builder.add(Merge[JsValue](topicsAndDigestRate.size))

        // Connects your source to the splitter's inlet
        builder.addEdge(in, splitter.in)
        splitter
          .topicOutlets
          .zipWithIndex
          // Repeats the wiring for each of the outlets of the splitter
          .foreach { case ((topic, port), index) =>
            val grouper = groupers(topic)
            val tagger = taggers(topic)
            // Connects the outlet to the splitter (the substream) to the grouper for this topic
            builder.addEdge(port, grouper.inlet)
            // Connects the outlet of the grouper to the inlet of the tagger
            builder.addEdge(grouper.outlet, tagger.inlet)
            // Connects the outlet of the tagger to one of the ports of the merger
            builder.addEdge(tagger.outlet, merger.in(index))
          }
        // Connects the outlet of the merger to the inlet of the output publisher
        builder.addEdge(merger.out, out.inlet)

      }
      // Runs the graph. The materialized result will be the publisher, which you can convert back to an enumerator.
      val publisher = graph.run()
      Streams.publisherToEnumerator(publisher)
    } getOrElse {
      Logger.error("Twitter credentials are not configured")
      Enumerator.empty[JsValue]
    }

  }


  // Retrieves the Twitter credentials from application.conf
  private def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey      <- Play.configuration.getString("twitter.apiKey")
    apiSecret   <- Play.configuration.getString("twitter.apiSecret")
    token       <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

}

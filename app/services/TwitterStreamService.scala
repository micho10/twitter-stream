package services

import javax.inject.Inject

import akka.actor.ActorSystem
import play.Logger
import play.api.Configuration
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator}
import play.api.libs.json.JsObject
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.{WS, WSAPI}
import play.extras.iteratees.{Encoding, JsonIteratees}

import scala.concurrent.ExecutionContext

import play.api.Play.current

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

}

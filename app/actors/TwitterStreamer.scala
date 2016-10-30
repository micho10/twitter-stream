package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.JsObject
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WS
import play.extras.iteratees.{Encoding, JsonIteratees}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by carlos on 27/10/16.
  */
class TwitterStreamer(out: ActorRef) extends Actor {

  // The receive method handles messages sent to this actor
  // Partial function (function defined only for some values of x)
  def receive = {
    // Handles the case of receiving a "subscribe" message
    case "subscribe" =>
      Logger.info("Received subscription from a client")
      // Sends out a simple Hello World message as a JSON object
      // "!" is an alias for the "tell" method, which means "fire & forget" a message without waiting for a reply nor
      // delivery confirmation
      TwitterStreamer.subscribe(out)
  }

}


object TwitterStreamer {
  // Helper method that initializes a new Props object.
  // Play will use the Props object to initialize the actor
  def props(out: ActorRef) = Props(new TwitterStreamer(out))

  // Initializes an empty variable to hold the broadcast enumerator
  private var broadcastEnumerator: Option[Enumerator[JsObject]] = None

  def connect(): Unit = {
    credentials.map { case (consumerKey, requestToken) =>
      // Sets up a joined set of iteratee & enumerator
      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

      // Sets up the stream transformation pipeline, taking data from the joined enumerator.
      // Each stage of the pipe is connected using the &> operation
      val jsonStream: Enumerator[JsObject] =
        enumerator &>
        Encoding.decode() &>
        Enumeratee.grouped(JsonIteratees.jsSimpleObject)

      // Initializes the broadcast enumerator using the transformed stream as a source
      val (be, _) = Concurrent.broadcast(jsonStream)
      broadcastEnumerator = Some(be)

      // Allow replicated nodes to connect to the master node.
      // Use the application configuration instead for production deployment.
      val maybeMasterNodeUrl = Option(System.getProperty("masterNodeUrl"))
      val url = maybeMasterNodeUrl.getOrElse {
        "https://stream.twitter.com/1.1/statuses/filter.json"
      }

      WS
        // The API URL
        .url(url)
        // OAuth signature of the request
        .sign(OAuthCalculator(consumerKey, requestToken))
        // Specifies a query string parameter
        .withQueryString("track" -> "cat")
        // Sends an HTTP GET request to the server and retrieves the response as a (possibly infinite) stream
        .get { response =>
          Logger.info("Status: " + response.status)
          // Provides the iteratee as the entry point of the data streamed through the HTTP connection. The stream
          // consumed by the iteratee will be passed on to the enumerator, which itself is the data source of the
          // jsonStream. All the data streaming takes place in a non-blocking fashion.
          // Consumes the stream from Twitter with the joined iteratee, which will pass it on to the joined enumerator
          iteratee
        }.map { _ =>
          // Returns a 200 OK result when the stream is entirely consumed or closed
          Logger.info("Twitter stream closed")
        }
    } getOrElse {
      Logger.error("Twitter credentials missing")
    }
  }


  def subscribe(out: ActorRef): Unit = {
    // Check if there's an initialized broadcast enumerator. If there's not, establish a connection
    if (broadcastEnumerator.isEmpty) connect()

    // Create a Twitter client iteratee JSON object to the browser using an actor reference
    val twitterClient = Iteratee.foreach[JsObject] { t => out ! t }
    broadcastEnumerator.foreach { enumerator =>
      enumerator run twitterClient
    }
  }


  /*
   * First, check the connection with Twitter is initialized. Then, returns the broadcasting enumeratee, which can be
   * used in an application controller.
   */
  def subscribeNode: Enumerator[JsObject] = {
    if (broadcastEnumerator.isEmpty) {
      connect()
    }
    broadcastEnumerator.getOrElse {
      Enumerator.empty[JsObject]
    }
  }


  // Retrieves the Twitter credentials from application.conf
  def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey      <- Play.configuration.getString("twitter.apiKey")
    apiSecret   <- Play.configuration.getString("twitter.apiSecret")
    token       <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (
    ConsumerKey(apiKey, apiSecret),
    RequestToken(token, tokenSecret)
    )

}

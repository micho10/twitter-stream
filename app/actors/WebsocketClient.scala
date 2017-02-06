package actors

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.CircuitBreaker
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.{WS, WSResponse}
import play.api.{Logger, Play}

import scala.concurrent.Future

/**
  * Created by carlos on 27/10/16.
  */
class WebsocketClient(out: ActorRef) extends Actor {

  import play.api.Play.current

  import scala.concurrent.duration._

  implicit val executionContext = context.dispatcher

  var timeout = 1

  val breaker = CircuitBreaker(
    scheduler = context.system.scheduler,
    maxFailures = 2,
    callTimeout = 5.seconds,
    resetTimeout = 5.seconds
  ).onOpen {
    Logger.error("Breaker open!!!!")
  }.onHalfOpen {
    Logger.warn("Breaker half open! Hang in there!")
    timeout = 1000
  }.onClose {
    Logger.info("Breaker closed, yay!")
  }


  override def receive = {
    case message: String =>
      Logger.info(s"Received message $message")

      credentials.map { case (consumerKey, requestToken) =>
        def searchResult: Future[WSResponse] = WS
          // The API URL
          .url("https://api.twitter.com/1.1/search/tweets.json")
          // Specifies a query string parameter
          .withQueryString("q" -> message)
          // OAuth signature of the request
          .sign(OAuthCalculator(consumerKey, requestToken))
          /***** Set an unrealistic timeout to simulate Twitter being unavailable. *****/
          .withRequestTimeout(timeout)
          // Sends an HTTP GET request to the server and retrieves the response as a (possibly infinite) stream
          .get()

        import akka.pattern.pipe

        /***** Not needed with a circuit breaker *****/
//        searchResult.map { result =>
//          SearchResult(result.body)
//        } recover { case NonFatal(t) =>
//          SearchFailure(t)
//        } pipeTo self

        /***** Using a circuit breaker *****/
        def mappedResult = searchResult.map { result =>
          SearchResult(result.body)
        }
          breaker.withCircuitBreaker(mappedResult) pipeTo self
      }

    case SearchResult(result) => out ! result

    case SearchFailure(t) => out ! s"Ooops, something went wrong: ${t.getMessage}"
  }


  // Retrieves the Twitter credentials from application.conf
  def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey      <- Play.configuration.getString("twitter.apiKey")
    apiSecret   <- Play.configuration.getString("twitter.apiSecret")
    token       <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

}



object WebsocketClient {
  def props(out: ActorRef) = Props(classOf[WebsocketClient], out)
}

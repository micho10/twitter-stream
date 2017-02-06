package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.{WS, WSResponse}
import play.api.{Logger, Play}

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Created by carlos on 27/10/16.
  */
class WebsocketClient(out: ActorRef) extends Actor {

  import play.api.Play.current

  implicit val executionContext = context.dispatcher

  override def receive = {
    case message: String =>
      Logger.info(s"Received message $message")

      credentials.map { case (consumerKey, requestToken) =>
        val searchResult: Future[WSResponse] = WS
          // The API URL
          .url("https://api.twitter.com/1.1/search/tweets.json")
          // Specifies a query string parameter
          .withQueryString("q" -> message)
          // OAuth signature of the request
          .sign(OAuthCalculator(consumerKey, requestToken))
          /***** Set an unrealistic timeout to simulate Twitter being unavailable. *****/
          .withRequestTimeout(1)
          // Sends an HTTP GET request to the server and retrieves the response as a (possibly infinite) stream
          .get()

        import akka.pattern.pipe

        searchResult.map { result =>
          SearchResult(result.body)
        } recover { case NonFatal(t) =>
          SearchFailure(t)
        } pipeTo self
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

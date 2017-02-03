package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import play.api.{Logger, Play}
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.{WS, WSResponse}

import scala.concurrent.Future

/**
  * Created by carlos on 27/10/16.
  */
class WebsocketClient(out: ActorRef) extends Actor {

  import play.api.Play.current

  implicit val executionContext = context.dispatcher

  override def receive = {
    case message: String =>
      credentials.map { case (consumerKey, requestToken) =>
        Logger.info(s"Received message $message")
        val response : Future[WSResponse] = WS
          // The API URL
          .url("https://stream.twitter.com/1.1/statuses/filter.json")
          // Specifies a query string parameter
          .withQueryString("q" -> message)
          // OAuth signature of the request
          .sign(OAuthCalculator(consumerKey, requestToken))
          // Sends an HTTP GET request to the server and retrieves the response as a (possibly infinite) stream
          .get()

        response.map { r =>
          out ! r.body
        }
      }
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

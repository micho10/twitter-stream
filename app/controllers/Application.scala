package controllers

import play.api.Play.current
import play.api._
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WS
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  // Uses "Action.async" to return a Future of a result for the next step
  def tweets = Action.async {
    def credentials: Option[ (ConsumerKey, RequestToken) ] = for {
      // Retrieves the Twitter credentials from application.conf
      apiKey      <- Play.configuration.getString("twitter.apiKey")
      apiSecret   <- Play.configuration.getString("twitter.apiSecret")
      token       <- Play.configuration.getString("twitter.token")
      tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
    } yield  (
      ConsumerKey(apiKey, apiSecret),
      RequestToken(token, tokenSecret)
      )

    credentials.map { case (consumerKey, requestToken) =>
      WS
        // The API URL
        .url("https://stream.twitter.com/1.1/statuses/filter.json")
        // OAuth signature of the request
        .sign(OAuthCalculator(consumerKey, requestToken))
        // Specifies a query string parameter
        .withQueryString("track" -> "reactive")
        // Executes an HTTP GET request
        .get()
        .map {
          response => Ok(response.body)
        }

    } getOrElse {
      // Wraps the result in a successful Future block to comply with the return type
      Future.successful {
        // Returns a 500 Internal Server Error if no credentials are available
        InternalServerError("Twitter credentials missing")
      }
    }
  }

}

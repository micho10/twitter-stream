package controllers

import play.api.Play.current
import play.api._
import play.api.libs.oauth.{ConsumerKey, RequestToken}
import play.api.mvc._

import scala.concurrent.Future

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  // Uses "Action.async" to return a Future of a result for the next step
  def tweets = Action.async {
    val credentials: Option[ (ConsumerKey, RequestToken) ] = for {
      // Retrieves the Twitter credentials from application.conf
      apiKey      <- Play.configuration.getString("twitter.apiKey")
      apiSecret   <- Play.configuration.getString("twitter.apiSecret")
      token       <- Play.configuration.getString("twitter.token")
      tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
    } yield  (
      ConsumerKey(apiKey, apiSecret)
      RequestToken(token, tokenSecret)
      )

    credentials.map { case (ConsumerKey, RequestToken) =>
      // Wraps the result in a successful Future block until the next step
      Future.successful { Ok }
    } getOrElse {
      // Wraps the result in a successful Future block to comply with the return type
      Future.successful {
        // Returns a 500 Internal Server Error if no credentials are available
        InternalServerError("Twitter credentials missing")
      }
    }
  }

}

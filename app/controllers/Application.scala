package controllers

import play.api.Play.current
import play.api._
import play.api.libs.iteratee.Iteratee
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

    // Defines a logging iteratee that consumes a stream asynchronously and logs the contents when the data is available
    val loggingIteratee = Iteratee.foreach[Array[Byte]] { array =>
      Logger.info(array.map(_.toChar).mkString)
    }

    credentials.map { case (consumerKey, requestToken) =>
      WS
        // The API URL
        .url("https://stream.twitter.com/1.1/statuses/filter.json")
        // OAuth signature of the request
        .sign(OAuthCalculator(consumerKey, requestToken))
        // Specifies a query string parameter
        .withQueryString("track" -> "reactive")
        // Sends an HTTP GET request to the server and retrieves the response as a (possibly infinite) stream
        .get { response =>
          Logger.info("Status: " + response.status)
          // Feeds the stream directly into the consuming logging iteratee;
          // the contents aren't loaded in memory first but are directly passed to the iteratee
          loggingIteratee
        }.map { _ =>
          // Returns a 200 OK result when the stream is entirely consumed or closed
          Ok("Stream closed")
        }

    } getOrElse {
      // Wraps the result in a successful Future block to comply with the return type
      Future.successful {
        // Returns a 500 Internal Server Error if no credentials are available
        InternalServerError("Twitter credentials missing")
      }
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

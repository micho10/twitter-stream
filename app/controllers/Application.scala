package controllers

import actors.TwitterStreamer
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc._

class Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("Tweets"))
  }

  def tweets = WebSocket.acceptWithActor[String, JsValue] {
    request => out => TwitterStreamer.props(out)
  }

  // Streaming the replicated Twitter feed
  def replicateFeed = Action { implicit request =>
    // Feeds the stream provided by the enumerator as an HTTP request
    Ok.feed(TwitterStreamer.subscribeNode)
  }

}

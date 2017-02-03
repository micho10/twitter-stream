package controllers

import actors.{TwitterStreamer, WebsocketClient}
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc._

class Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("Messages"))
  }

  def stream = Action { implicit request =>
    Ok(views.html.stream("Tweets"))
  }

  def messages = WebSocket.acceptWithActor[String, JsValue] { implicit request =>
    out => TwitterStreamer.props(out)
  }

  // Streaming the replicated Twitter feed
  def replicateFeed = Action { implicit request =>
    // Feeds the stream provided by the enumerator as an HTTP request
    Ok.feed(TwitterStreamer.subscribeNode)
  }

  def socket = WebSocket.acceptWithActor[String, String]{ implicit request =>
    out => WebsocketClient.props(out)
  }

}

package controllers

import javax.inject.Inject

import actors.{TwitterStreamer, WebsocketClient}
import play.api.Play.current
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.mvc._
import services.TwitterStreamService

class Application @Inject()(twitterstream: TwitterStreamService) extends Controller {

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

  def strindex = Action { implicit request =>
    val parsedTopics = parseTopicsAndDigestRate(request.queryString)
    Ok(views.html.strindex(parsedTopics, request.rawQueryString))
  }

  /**
    * Creates a WebSocket; the channels will use JsValue as the format
    *
    * @return
    */
  def reactiveStream = WebSocket.using[JsValue] { implicit request =>
    val parsedTopics = parseTopicsAndDigestRate(request.queryString)
    // Creates the output enumerator using the streaming service you've built
    val out = twitterstream.stream(parsedTopics)
    // Ignores any messages coming from the client
    val in: Iteratee[JsValue, Unit] = Iteratee.ignore[JsValue]
    // Returns the pair of input and output channels required to build the WebSocket
    (in, out)
  }

  private def parseTopicsAndDigestRate(queryString: Map[String, Seq[String]]): Map[String, Int] = {
    val topics = queryString.getOrElse("topic", Seq.empty)
    topics.map { topicAndRate =>
      val Array(topic, digestRate) = topicAndRate.split(':')
      (topic, digestRate.toInt)
    }.toMap[String, Int]
  }

}

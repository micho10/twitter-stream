package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.Logger
import play.api.libs.json.Json

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
      out ! Json.obj("text" -> "Hello, world!")
  }

}

object TwitterStreamer {
  // Helper method that initializes a new Props object.
  // Play will use the Props object to initialize the actor
  def props(out: ActorRef) = Props(new TwitterStreamer(out))
}

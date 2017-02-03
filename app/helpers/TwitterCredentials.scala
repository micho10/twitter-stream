package helpers

import play.api.Play
import play.api.libs.oauth.{ConsumerKey, RequestToken}

/**
  * Created by carlos on 27/10/16.
  */
trait TwitterCredentials {

  import play.api.Play.current

  // Retrieves the Twitter credentials from application.conf
  protected def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey      <- Play.configuration.getString("twitter.apiKey")
    apiSecret   <- Play.configuration.getString("twitter.apiSecret")
    token       <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

}

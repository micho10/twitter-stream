name := """twitter-stream"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "scalaz-bintray"   at "http://dl.bintray.com/scalaz/releases",
  "Typesafe private" at "https://private-repo.typesafe.com/typesafe/maven-releases"
)

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2                      %   Test,
  // Workaround for a bug in Play 2.4 with OAuth
  "com.ning"                  %   "async-http-client"             % "1.9.40",
  "com.typesafe.play.extras"  %%  "iteratees-extras"              % "1.5.0",
  "com.typesafe.play"         %%  "play-streams-experimental"     % "2.4.2",
  "com.typesafe.akka"         %   "akka-stream-experimental_2.11" % "1.0"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

resolvers += "Typesafe private" at "https://private-repo.typesafe.com/typesafe/maven-releases"

// Play provides two styles of routers, one expects its actions to be injected,
// the other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

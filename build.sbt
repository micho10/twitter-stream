name := """twitter-stream"""

version := "1.0-SNAPSHOT"

//scalaVersion := "2.11.8"
lazy val scalaV = "2.11.6"

//lazy val root = (project in file(".")).enablePlugins(PlayScala)
// Defines the root Play project
lazy val `reactive-stream` = (project in file(".")).settings(
  scalaVersion := scalaV,
  // Indicates which projects are Scala.js projects
  scalaJSProjects := Seq(client),
  // Defines the sbt-web pipeline stages: in this case, the generation of optimized Scala.js artifacts for production
  pipelineStages := Seq(scalaJSProd),
  // Includes a library that helps with referencing Scala.js artifacts in Twirl templates
  libraryDependencies ++= Seq(
    "com.vmunier" %% "play-scalajs-scripts" % "0.2.2"
  ),
  // Directly imports the artifacts of the client module without wrapping it in an intermediary Webjar
  WebKeys.importDirectly := true
).enablePlugins(PlayScala).dependsOn(client).aggregate(client)

// Defines the client Scala.js project
lazy val client = (project in file("modules/client")).settings(
  scalaVersion := scalaV,
  persistLauncher := true,
  persistLauncher in Test := false,
  // Includes the scalajs-dom library for DOM manipulation
  libraryDependencies ++= Seq(
    "org.scala-js"  %%% "scalajs-dom" % "0.8.0"
  ),
  skip in packageJSDependencies := false
  // Loads the scalajs, scalajs-play, and sbt-web plugins
  ).enablePlugins(ScalaJSPlugin, ScalaJSPlay, SbtWeb)

resolvers ++= Seq(
  "scalaz-bintray"      at "http://dl.bintray.com/scalaz/releases",
  "Typesafe private"    at "https://private-repo.typesafe.com/typesafe/maven-releases"
)

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2                      %   Test,
  // Workaround for a bug in Play 2.4 with OAuth
  "com.ning"                  %   "async-http-client" % "1.9.40",
  "com.typesafe.play.extras"  %%  "iteratees-extras"  % "1.5.0"
)

// Play provides two styles of routers, one expects its actions to be injected,
// the other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator


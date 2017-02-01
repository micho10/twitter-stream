resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases"


// The Play plugin
addSbtPlugin("com.typesafe.play"  % "sbt-plugin"        % "2.4.8")

// web plugins

addSbtPlugin("com.typesafe.sbt"   % "sbt-coffeescript"  % "1.0.0")

addSbtPlugin("com.typesafe.sbt"   % "sbt-less"          % "1.0.6")

addSbtPlugin("com.typesafe.sbt"   % "sbt-jshint"        % "1.0.3")

addSbtPlugin("com.typesafe.sbt"   % "sbt-rjs"           % "1.0.7")

addSbtPlugin("com.typesafe.sbt"   % "sbt-digest"        % "1.1.0")

addSbtPlugin("com.typesafe.sbt"   % "sbt-mocha"         % "1.1.0")

// The standard Play sbt plugin
addSbtPlugin("com.typesafe.play"  % "sbt-plugin"        % "2.4.3")

// The sbt-play-scalajs sbt plugin that combines Play and Scala,js
addSbtPlugin("com.vmunier"        % "sbt-play-scalajs"  % "0.2.6")

// The Scala.js sbt plugin
addSbtPlugin("org.scala-js"       % "sbt-scalajs"       % "0.6.3")

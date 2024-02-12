name := """HotPursuit"""
organization := "com.PTGame"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.12"

libraryDependencies += guice
libraryDependencies ++= Seq(
   "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0" % Test,
   "org.apache.hadoop" % "hadoop-common" % "3.3.4")
/*hadoop dependency required only for its FileSystem and Path methods that make it relatively
easy to read files from disparate filesystems. Included in case project needs to be altered
and files need to be read from s3 bucket.
*/
libraryDependencies += ws
libraryDependencies += ehcache


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.firstplay.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.firstplay.binders._"

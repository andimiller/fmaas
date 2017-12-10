name := "fmaas"

version := "0.1.0"

scalaVersion := "2.12.4"

val http4sVersion = "0.18.0-M6"
val fs2Version    = "0.10.0-M9"
val circeVersion  = "0.9.0-M2"
val catsVersion   = "1.0.0-RC1"

libraryDependencies ++= Seq("http4s-core", "http4s-dsl", "http4s-blaze-server", "http4s-circe").map("org.http4s" %% _ % http4sVersion)
libraryDependencies ++= Seq("circe-core", "circe-generic", "circe-parser").map("io.circe" %% _ % circeVersion)
libraryDependencies ++= Seq("com.monovore" %% "decline" % "0.4.0-RC1")
// separate this out maybe?
libraryDependencies += "io.circe" %% "circe-yaml" % "0.7.0-M2"

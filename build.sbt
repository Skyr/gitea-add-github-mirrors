import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "de.ploing",
      scalaVersion := "2.12.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Gitea add GitHub mirrors",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.3.2",
      "com.typesafe.play" %% "play-json" % "2.6.9",
      "com.squareup.okhttp3" % "okhttp" % "3.11.0",
      scalaTest % Test
    )
  )

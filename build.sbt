name := """crypto-tax"""
organization := "pstepniewski"

version := "1.0-SNAPSHOT"

lazy val `crypto-tax` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.5"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice, evolutions )

libraryDependencies += "com.typesafe.play" %% "anorm" % "2.5.3"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.2"

unmanagedResourceDirectories in Test +=  baseDirectory.value / "target/web/public/test"

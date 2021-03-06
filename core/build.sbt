organization := "lt.tabo"

name := "json2s"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "org.json4s" %% "json4s-native" % "3.2.10",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

resolvers += Resolver.sonatypeRepo("public")

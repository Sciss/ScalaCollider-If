name         := "ScalaCollider-If"
version      := "0.1.0-SNAPSHOT"
organization := "at.iem.sysson"
description  := "If-Then-Else blocks for ScalaCollider using nested, resource-efficient UGen graphs"
homepage     := Some(url(s"https://github.com/iem-projects/${name.value}"))
licenses     := Seq("lgpl" -> url("https://www.gnu.org/licenses/lgpl-2.1.txt"))
scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

lazy val scalaColliderVersion = "1.20.1"
lazy val ugensVersion         = "1.15.3"

libraryDependencies ++= Seq(
  "de.sciss" %% "scalacollider" % scalaColliderVersion
)

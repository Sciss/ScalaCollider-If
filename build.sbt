lazy val baseName  = "ScalaCollider-If"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "0.3.1-SNAPSHOT"
lazy val mimaVersion    = "0.3.0"

name                := baseName
version             := projectVersion
organization        := "at.iem"
description         := "If-Then-Else blocks for ScalaCollider using nested, resource-efficient UGen graphs"
homepage            := Some(url(s"https://github.com/iem-projects/${name.value}"))
licenses            := Seq("lgpl" -> url("https://www.gnu.org/licenses/lgpl-2.1.txt"))
scalaVersion        := "2.11.8"
crossScalaVersions  := Seq("2.12.1", "2.11.8", "2.10.6")
scalacOptions      ++= {
  val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")
  if (loggingEnabled || isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")
}

mimaPreviousArtifacts := Set("at.iem" %% baseNameL % mimaVersion)

lazy val loggingEnabled = false

// ---- main dependencies ----

lazy val scalaColliderVersion = "1.22.3"

// ---- test dependencies ----

lazy val scalaTestVersion     = "3.0.1"
lazy val fileUtilVersion      = "1.1.2"

libraryDependencies ++= Seq(
  "de.sciss"      %% "scalacollider"  % scalaColliderVersion,
  "org.scalatest" %% "scalatest"      % scalaTestVersion    % "test",
  "de.sciss"      %% "fileutil"       % fileUtilVersion     % "test"
)

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (isSnapshot.value)
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
  <scm>
    <url>git@github.com:iem-projects/{n}.git</url>
    <connection>scm:git:git@github.com:iem-projects/{n}.git</connection>
  </scm>
    <developers>
      <developer>
        <id>sciss</id>
        <name>Hanns Holger Rutz</name>
        <url>http://www.sciss.de</url>
      </developer>
    </developers>
}

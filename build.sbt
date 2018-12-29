lazy val baseName  = "ScalaCollider-If"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "0.9.0"
lazy val mimaVersion    = "0.9.0"

lazy val deps = new {
  val main = new {
    val scalaCollider = "1.28.0"
  }
  val test = new {
    val scalaTest     = "3.0.5"
    val fileUtil      = "1.1.3"
  }
}

lazy val loggingEnabled = false

lazy val root = project.withId(baseNameL).in(file("."))
  .settings(
    name                := baseName,
    version             := projectVersion,
    organization        := "de.sciss",
    description         := "If-Then-Else blocks for ScalaCollider using nested, resource-efficient UGen graphs",
    homepage            := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses            := Seq("lgpl" -> url("https://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion        := "2.13.0-M5",
    crossScalaVersions  := Seq("2.12.8", "2.11.12", "2.13.0-M5"),
    scalacOptions      ++= {
      val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")
      if (loggingEnabled || isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")
    },
    mimaPreviousArtifacts := Set(organization.value %% baseNameL % mimaVersion),
    libraryDependencies ++= Seq(
      "de.sciss"      %% "scalacollider"  % deps.main.scalaCollider,
      "de.sciss"      %% "fileutil"       % deps.test.fileUtil     % Test
    ),
    libraryDependencies += {
      val v = if (scalaVersion.value == "2.13.0-M5") "3.0.6-SNAP5" else deps.test.scalaTest
      "org.scalatest" %% "scalatest" % v % Test
    }
  )
  .settings(publishSettings)

// ---- publishing ----
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
  }
)

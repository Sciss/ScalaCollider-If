lazy val baseName  = "ScalaCollider-If"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "0.6.0"
lazy val mimaVersion    = "0.6.0"

lazy val deps = new {
  val main = new {
    val scalaCollider = "1.25.0"
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
    homepage            := Some(url(s"https://github.com/Sciss/${name.value}")),
    licenses            := Seq("lgpl" -> url("https://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion        := "2.12.5",
    crossScalaVersions  := Seq("2.12.5", "2.11.12"),
    scalacOptions      ++= {
      val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")
      if (loggingEnabled || isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")
    },
    mimaPreviousArtifacts := Set(organization.value %% baseNameL % mimaVersion),
    libraryDependencies ++= Seq(
      "de.sciss"      %% "scalacollider"  % deps.main.scalaCollider,
      "org.scalatest" %% "scalatest"      % deps.test.scalaTest    % Test,
      "de.sciss"      %% "fileutil"       % deps.test.fileUtil     % Test
    )
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
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
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

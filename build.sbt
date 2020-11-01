lazy val baseName  = "ScalaCollider-If"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "1.2.0-SNAPSHOT"
lazy val mimaVersion    = "1.2.0"

lazy val deps = new {
  val main = new {
    val scalaCollider = "2.2.0-SNAPSHOT"
  }
  val test = new {
    val scalaTest     = "3.2.2"
    val fileUtil      = "1.1.5"
  }
}

lazy val loggingEnabled = false

lazy val commonJvmSettings = Seq(
  crossScalaVersions  := Seq("0.27.0-RC1", "2.13.3", "2.12.12"),
)

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .jvmSettings(commonJvmSettings)
  .settings(
    name                := baseName,
    version             := projectVersion,
    organization        := "de.sciss",
    description         := "If-Then-Else blocks for ScalaCollider using nested, resource-efficient UGen graphs",
    homepage            := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses            := Seq("lgpl" -> url("https://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion        := "2.13.3",
    scalacOptions      ++= {
      val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint", "-Xsource:2.13")
      val ys = if (loggingEnabled || isSnapshot.value || isDotty.value) xs else xs ++ Seq("-Xelide-below", "INFO")
      val sv = scalaVersion.value
      if (sv.startsWith("2.13.")) ys :+ "-Wvalue-discard" else ys
    },
    mimaPreviousArtifacts := Set(organization.value %% baseNameL % mimaVersion),
    libraryDependencies ++= Seq(
      "de.sciss"      %%% "scalacollider" % deps.main.scalaCollider,
      "org.scalatest" %%% "scalatest"     % deps.test.scalaTest % Test,
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "de.sciss" %% "fileutil" % deps.test.fileUtil % Test,
    ),
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

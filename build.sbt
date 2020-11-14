lazy val baseName  = "ScalaCollider-If"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "1.5.0"
lazy val mimaVersion    = "1.5.0"

lazy val deps = new {
  val main = new {
    val scalaCollider = "2.4.0"
  }
  val test = new {
    val scalaTest     = "3.2.3"
//    val fileUtil      = "1.1.5"
  }
}

lazy val loggingEnabled = false

lazy val commonJvmSettings = Seq(
  crossScalaVersions  := Seq("3.0.0-M1", "2.13.3", "2.12.12"),
)

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .jvmSettings(commonJvmSettings)
  .settings(
    name                := baseName,
    version             := projectVersion,
    organization        := "de.sciss",
    description         := "If-Then-Else blocks for ScalaCollider using nested, resource-efficient UGen graphs",
    homepage            := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses             := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
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
//  .jvmSettings(
//    libraryDependencies ++= Seq(
//      "de.sciss" %% "fileutil" % deps.test.fileUtil % Test,
//    ),
//  )
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

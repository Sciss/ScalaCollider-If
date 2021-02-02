lazy val baseName  = "ScalaCollider-If"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "1.7.1"
lazy val mimaVersion    = "1.7.0"

lazy val deps = new {
  val main = new {
    val scalaCollider = "2.6.1"
  }
  val test = new {
    val scalaTest     = "3.2.3"
  }
}

lazy val loggingEnabled = false

lazy val commonJvmSettings = Seq(
  crossScalaVersions  := Seq("3.0.0-M3", "2.13.4", "2.12.12"),
)

// sonatype plugin requires that these are in global
ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .jvmSettings(commonJvmSettings)
  .settings(
    name                := baseName,
//    version             := projectVersion,
//    organization        := "de.sciss",
    description         := "If-Then-Else blocks for ScalaCollider using nested, resource-efficient UGen graphs",
    homepage            := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses             := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
    scalaVersion        := "2.13.4",
    scalacOptions      ++= {
      val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8")
      val ys = if (loggingEnabled || isSnapshot.value || isDotty.value) xs else xs ++ Seq("-Xelide-below", "INFO")
      val sv = scalaVersion.value
      if (sv.startsWith("2.13.")) ys :+ "-Wvalue-discard" else ys
    },
    scalacOptions ++= {
      if (isDotty.value) Nil else Seq("-Xlint", "-Xsource:2.13")
    },
    sources in (Compile, doc) := {
      if (isDotty.value) Nil else (sources in (Compile, doc)).value // dottydoc is complaining about something
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
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "git.iem.at"
    val a = s"sciss/$baseName"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)


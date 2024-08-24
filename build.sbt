val scala3Version = "3.4.2"

val circe = Seq(
  "circe-core",
  "circe-generic",
  "circe-parser"
).map("io.circe" %% _ % "0.14.1")

def zioLib(name: String)     = "dev.zio"                   %% name % "2.1.7"
def jacksonLib(name: String) = "com.fasterxml.jackson.core" % name % "2.17.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "HAR Resource Extractor",
    version := "0.2.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= List(
      zioLib("zio"),
      zioLib("zio-streams"),
//      "com.softwaremill.quicklens" %% "quicklens" % "1.9.7",
//      jacksonLib("jackson-core"),
//      jacksonLib("jackson-databind"),
      "com.github.jsurfer" % "jsurfer-jackson" % "1.6.4"
    )
  )

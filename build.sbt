val scala3Version = "3.2.2"
val circeVersion  = "0.14.1"

val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

lazy val root = project
  .in(file("."))
  .settings(
    name := "HAR Resource Extractor",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= List(
      "dev.zio"                    %% "zio"       % "2.0.13",
      "com.softwaremill.quicklens" %% "quicklens" % "1.9.4"
    ) ++ circe
  )

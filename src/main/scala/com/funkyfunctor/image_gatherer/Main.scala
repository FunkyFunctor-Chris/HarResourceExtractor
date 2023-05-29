package com.funkyfunctor.image_gatherer

import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import zio.{Scope, Task, ZIO, ZIOApp, ZIOAppArgs, ZIOAppDefault}

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.{Base64, UUID}
import scala.io.Source

// Define case classes to represent the HAR structure
case class HarLog(log: HarData)
case class HarData(entries: List[HarEntry])
case class HarEntry(request: HarRequest, response: HarResponse)
case class HarRequest(method: String, url: String)
case class HarResponse(status: Int, content: HarContent)
case class HarContent(size: Long, mimeType: String, text: Option[String])

object Main extends ZIOAppDefault {
  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    for {
      // We get the HAR
      args <- getArgs
      file <-
        if (args.isEmpty)
          ZIO.fail("Missing argument to indicate the input HAR file")
        else ZIO.succeed(args.head)
      pageString <- ZIO.attempt(Source.fromFile(file).mkString)
      harLog     <- ZIO.fromEither(decode[HarLog](pageString))

      // We get the list of images
      resources = harLog.log.entries.filter { entry =>
        entry.response.status == 200 &&
        entry.response.content.mimeType == "image/jpeg"
      }

      // We download the images
      outputFolder <- ZIO.attempt {
        Files.createDirectory(Path.of(file + "_output")).toFile
      }
      result <- ZIO.foreachPar(resources)(getContent(outputFolder, _))
    } yield ()
  }

  def getContent(outputFolder: File, entry: HarEntry): ZIO[Any, Throwable, Unit] = {
    entry.response.content.text match {
      case None => ZIO.unit
      case Some(content) =>
        for {
          binaryContent <- ZIO.attempt(Base64.getDecoder.decode(content))
          path          <- getFileName(outputFolder, entry)
          _ <- ZIO.attempt(
            Files.write(
              path,
              binaryContent,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE
            )
          )
          _ <- ZIO.logInfo(s"Wrote content to $path")
        } yield ()
    }
  }

  private val urlRegex = """.*/([^/]+)$""".r

  def getFileName(parentFolder: File, entry: HarEntry): Task[Path] = {
    {
      entry.request.url match {
        case urlRegex(fileName) =>
          ZIO.attempt(new File(parentFolder, fileName))
        case _ =>
          ZIO.attempt(File.createTempFile("tmp" + UUID.randomUUID(), ".jpg", parentFolder))
      }
    }.map(_.getAbsolutePath)
      //.tap(path => ZIO.log(s"Attempting to write to ${path}"))
      .map(Path.of(_))
  }
}

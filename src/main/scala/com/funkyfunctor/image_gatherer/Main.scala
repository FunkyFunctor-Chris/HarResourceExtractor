package com.funkyfunctor.image_gatherer

import com.funkyfunctor.image_gatherer.JsonObjects.HarEntry
import io.circe.*
import zio.*

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.{Base64, UUID}

object Main extends ZIOAppDefault {
  override def run: ZIO[ZIOAppArgs & Scope, Any, Unit] = {
    for {
      // We get the HAR
      args <- getArgs
      path <-
        if (args.isEmpty)
          ZIO.fail("Missing argument to indicate the input HAR file")
        else ZIO.succeed(args.head)
      file <- ZIO.attempt {
        val f = new File(path)

        if (f.exists())
          f
        else throw new Exception(s"File $f does not exist")
      }
      outputFolder <- createOutputFolder(path, args)
      _ <- JacksonStream
        .getStream(file)
        .filter { entry =>
          entry.response.status == 200 &&
          entry.response.content.mimeType.startsWith("image/")
        }
        .foreach(persistContent(outputFolder, _))
    } yield ()
  }

  private def createOutputFolder(file: String, args: Chunk[String]): Task[File] = ZIO.attempt {
    val fic = if (args.length >= 2) {
      new File(args(1))
    } else new File(file + "_output")

    if (!fic.exists())
      Files.createDirectory(Path.of(fic.toURI))

    fic
  }

  private def persistContent(outputFolder: File, entry: HarEntry): UIO[Unit] = {
    entry.response.content.text match {
      case None => ZIO.unit
      case Some(content) =>
        {
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
          } yield path
        }.foldZIO(
          failure => ZIO.logError(s"Failed to write ${entry.request.url} to disk: $failure"),
          path => ZIO.logInfo(s"Wrote content to $path")
        )
    }
  }

  private val urlRegex = """.*/([^/?]+).*$""".r

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

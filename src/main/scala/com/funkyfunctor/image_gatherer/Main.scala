package com.funkyfunctor.image_gatherer

import com.funkyfunctor.image_gatherer.JsonObjects.HarEntry
import zio.*
import zio.stream.{ZSink, ZStream}

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.{Base64, UUID}

object Main extends ZIOAppDefault {
  private val base64Decoder = Base64.getDecoder

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
        .flatMapPar(8)(decodeEntryContent(outputFolder, _))
        .flatMapPar(4)(persistContent)
        .runScoped(ZSink.drain)
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

  private def decodeEntryContent(
      outputFolder: File,
      entry: HarEntry
  ): ZStream[Any, Nothing, (Path, Array[Byte])] =
    ZStream.fromZIO {
      {
        entry.response.content.text match {
          case None =>
            ZIO.logError(
              s"No content to decode for ${entry.request.url} (${entry.request.method})"
            ) *> ZIO.fail(())
          case Some(content) =>
            getFileName(outputFolder, entry) zipPar ZIO.attempt(base64Decoder.decode(content))
        }
      }.fold(
        _ => ZStream.empty,
        ZStream(_)
      )
    }.flatten

  private def persistContent(entry: (Path, Array[Byte])): ZStream[Any, Nothing, Unit] = {
    val (path, binaryContent) = entry

    ZStream.fromZIO(
      ZIO.attempt(
        Files.write(
          path,
          binaryContent,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE
        )
      ).foldZIO(
        failure => ZIO.logError(s"Failed to write $path to disk: $failure"),
        path => ZIO.logInfo(s"Wrote content to $path")
      )
    )
  }

  private val urlRegex = """.*/([^/?]+).*$""".r

  def getFileName(parentFolder: File, entry: HarEntry): Task[Path] = {
    {
      entry.request.url match {
        case urlRegex(fileName) =>
          ZIO.attempt(new File(parentFolder, fileName))
        case _ =>
          ZIO.attempt(
            File.createTempFile("tmp" + UUID.randomUUID(), getDefaultExtension(entry), parentFolder)
          )
      }
    }.map(_.getAbsolutePath)
      //.tap(path => ZIO.log(s"Attempting to write to ${path}"))
      .map(Path.of(_))
  }

  def getDefaultExtension(entry: HarEntry): String = {
    entry.response.content.mimeType match {
      case "image/jpeg"    => ".jpg"
      case "image/png"     => ".png"
      case "image/gif"     => ".gif"
      case "image/bmp"     => ".bmp"
      case "image/tiff"    => ".tiff"
      case "image/webp"    => ".webp"
      case "image/svg+xml" => ".svg"
      case _               => ".jpg"
    }
  }
}

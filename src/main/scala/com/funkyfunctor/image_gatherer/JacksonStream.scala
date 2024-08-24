package com.funkyfunctor.image_gatherer

import com.funkyfunctor.image_gatherer.JsonObjects.HarEntry
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import org.jsfr.json.compiler.JsonPathCompiler
import org.jsfr.json.provider.JacksonProvider
import org.jsfr.json.{JacksonParser, JsonSurfer}
import zio.*
import zio.Console.printLine
import zio.stream.ZStream

import java.io.{File, FileInputStream}
import scala.jdk.CollectionConverters.*

object JacksonStream extends ZIOAppDefault {
  override def run: ZIO[ZIOAppArgs & Scope, Any, Unit] = {
    getStream(new File("/home/candre/mkyong.com.har"))
      .foreach(entry => printLine(entry.request.method + "\t" + entry.request.url))
  }

  private def loadEntries(file: File) = {
    for {
      fis    <- ZIO.attempt(new FileInputStream(file))
      surfer <- ZIO.attempt(new JsonSurfer(JacksonParser.INSTANCE, JacksonProvider.INSTANCE))
      it     <- ZIO.attempt(surfer.iterator(fis, JsonPathCompiler.compile("$..entries[*]")))
    } yield it.asScala
  }

  def getStream(file: File): ZStream[Any, Throwable, HarEntry] = {
    ZStream
      .fromIteratorZIO(loadEntries(file))
      .flatMap {
        case on: com.fasterxml.jackson.databind.node.ObjectNode => ZStream(on.toString)
        case _                                                  => ZStream.empty
      }
      .flatMap {
        decode[HarEntry](_) match {
          case Left(_)      => ZStream.empty
          case Right(value) => ZStream(value)
        }
      }
  }
}

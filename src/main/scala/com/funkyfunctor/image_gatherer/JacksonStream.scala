package com.funkyfunctor.image_gatherer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.funkyfunctor.image_gatherer.JsonObjects.*
import org.jsfr.json.compiler.JsonPathCompiler
import org.jsfr.json.provider.JacksonProvider
import org.jsfr.json.{JacksonParser, JsonSurfer, JsonSurferJackson}
import zio.*
import zio.Console.printLine
import zio.stream.ZStream

import java.io.{BufferedInputStream, File, FileInputStream}
import scala.jdk.CollectionConverters.*

object JacksonStream {
  private def loadEntries(file: File) = {
    for {
      fis    <- ZIO.fromAutoCloseable(ZIO.attempt(new BufferedInputStream(new FileInputStream(file))))
      it     <- ZIO.attempt(JsonSurferJackson.INSTANCE.iterator(fis, JsonPathCompiler.compile("$..entries[*]")))
    } yield it.asScala
  }

  def getStream(file: File) = {
    ZStream
      .fromIteratorZIO(loadEntries(file))
      .flatMap {
        case on: ObjectNode =>
          val effect = fromObjectNodetoHarEntry(on).fold(
            _ => ZStream.empty,
            ZStream(_)
          )

          ZStream.fromZIO(effect).flatten
        case _ => ZStream.empty
      }
  }

  def fromObjectNodetoHarEntry(node: ObjectNode): IO[Option[Nothing], HarEntry] =
    ZIO.fromOption(toHarEntry(node))

  def toHarEntry(node: JsonNode): Option[HarEntry] = {
    for {
      requestJson  <- Option(node.get("request"))
      request      <- toRequest(requestJson)
      responseJson <- Option(node.get("response"))
      response     <- toResponse(responseJson)
    } yield HarEntry(request, response)
  }

  def toRequest(node: JsonNode): Option[HarRequest] = {
    for {
      method <- Option(node.get("method")).map(_.asText)
      url    <- Option(node.get("url")).map(_.asText)
    } yield HarRequest(method, url)
  }

  def toResponse(node: JsonNode): Option[HarResponse] = {
    for {
      status      <- Option(node.get("status")).map(_.asInt)
      contentJson <- Option(node.get("content"))
      content     <- toContent(contentJson)
    } yield HarResponse(status, content)
  }

  def toContent(node: JsonNode): Option[HarContent] = {
    for {
      size     <- Option(node.get("size")).map(_.asLong)
      mimeType <- Option(node.get("mimeType")).map(_.asText)
      text = Option(node.get("text")).map(_.asText)
    } yield HarContent(size, mimeType, text)
  }
}

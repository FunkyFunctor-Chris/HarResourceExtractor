package com.funkyfunctor.image_gatherer

object JsonObjects {
  // Define case classes to represent the HAR structure
//  case class HarLog(log: HarData)
//
  case class HarData(entries: List[HarEntry])

  case class HarEntry(request: HarRequest, response: HarResponse)

  case class HarRequest(method: String, url: String)

  case class HarResponse(status: Int, content: HarContent)

  case class HarContent(size: Long, mimeType: String, text: Option[String])
}

package com.ivmoreau.localservices.model

import skunk._
import skunk.codec.all._

case class Request(
    id: Int,
    serviceDescription: String,
    requestDate: String,
    location: String,
    providerId: Int,
    clientId: Int
)

object Request {
  private val structure =
    int4 *: text *: varchar(255) *: varchar(255) *: int4 *: int4
  given skunkDecoder: Decoder[Request] = structure.to[Request]
  given skunkEncoder: Encoder[Request] = structure.values.to[Request]
}

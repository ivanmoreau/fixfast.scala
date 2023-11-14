package com.ivmoreau.localservices.model

import skunk.*
import skunk.codec.all.*

case class Provider(
    id: Int,
    name: String
)

object Provider:
  val skunkDecoder: Decoder[Provider] =
    (int4 ~ varchar).to[Provider]
  val skunkEncoder: Encoder[Provider] =
    (int4 ~ varchar).values.to[Provider]
end Provider

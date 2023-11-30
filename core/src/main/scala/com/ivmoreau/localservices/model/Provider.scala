package com.ivmoreau.localservices.model

import skunk.*
import skunk.codec.all.*

case class Provider(
    id: Int,
    name: String
)

object Provider:
  val skunkDecoder: Decoder[Provider] =
    (int4 ~ varchar(255)).to[Provider]
  val skunkEncoder: Encoder[Provider] =
    (int4 ~ varchar(255)).values.to[Provider]
end Provider

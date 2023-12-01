package com.ivmoreau.localservices.model

import skunk.*
import skunk.codec.all.*

case class Provider(
    id: Int,
    name: String,
    category: String
)

object Provider:
  val qq: Codec[(Int, String, String)] =
    (int4 *: varchar(255) *: varchar(255))
  val skunkDecoder: Decoder[Provider] =
    qq.to[Provider]
  val skunkEncoder: Encoder[Provider] =
    qq.values.to[Provider]
end Provider

package com.ivmoreau.localservices.model

import skunk.*
import skunk.codec.all.*

case class Client(
    id: Int,
    name: String
)

object Client:
  val skunkDecoder: Decoder[Client] =
    (int4 ~ varchar).to[Client]
  val skunkEncoder: Encoder[Client] =
    (int4 ~ varchar).values.to[Client]
end Client

package com.ivmoreau.localservices.model

import skunk.*
import skunk.codec.all.*

case class User(
    email: String,
    passwordHash: String,
    clientId: Option[Int],
    providerId: Option[Int]
)

object User:
  val skunkDecoder: Decoder[User] =
    (varchar *: varchar *: int4.opt *: int4.opt).to[User]
  val skunkEncoder: Encoder[User] =
    (varchar *: varchar *: int4.opt *: int4.opt).values.to[User]
end User

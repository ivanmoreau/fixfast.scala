package com.ivmoreau.localservices.model

import skunk.*
import skunk.codec.all.*

case class User(
    email: String,
    passwordHash: String,
    address: String,
    clientId: Option[Int],
    providerId: Option[Int]
)

object User:
  private val structure =
    varchar(255) *: varchar(255) *: varchar(255) *: int4.opt *: int4.opt
  given skunkDecoder: Decoder[User] = structure.to[User]
  given skunkEncoder: Encoder[User] = structure.values.to[User]
end User

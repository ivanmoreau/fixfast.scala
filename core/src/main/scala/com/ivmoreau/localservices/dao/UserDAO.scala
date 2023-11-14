package com.ivmoreau.localservices.dao

import cats.effect.{IO, Resource}
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import com.ivmoreau.localservices.model.User

trait UserDAO:
  def fetchUser(email: String): IO[Option[User]]
  def insertUser(user: User): IO[Unit]
end UserDAO

case class UserDAOSkunkImpl(
    skunkConnection: Resource[IO, Session[IO]]
) extends UserDAO:
  private val userQuery: Query[String, User] =
    sql"""
      SELECT email, password_hash, client_id, provider_id
      FROM user
      WHERE email = $varchar
    """.query(User.skunkDecoder)
  end userQuery

  override def fetchUser(email: String): IO[Option[User]] =
    skunkConnection.use(
      _.execute(userQuery)(email).map(_.headOption)
    )
  end fetchUser

  private val userInsert: Command[User] =
    sql"""
      INSERT INTO user VALUES ($varchar, $varchar, ${int4.opt}, ${int4.opt})
    """.command.to[User]
  end userInsert

  override def insertUser(user: User): IO[Unit] =
    skunkConnection.use(
      _.prepare(userInsert).flatMap(_.execute(user)).void
    )
  end insertUser
end UserDAOSkunkImpl

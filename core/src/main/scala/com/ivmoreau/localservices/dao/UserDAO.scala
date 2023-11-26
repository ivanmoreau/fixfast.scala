package com.ivmoreau.localservices.dao

import cats.data.OptionT
import cats.effect.{IO, Resource}
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import com.ivmoreau.localservices.model.User
import tsec.authentication.IdentityStore

trait UserDAO:
  def fetchUser(email: String): IO[Option[User]]
  def insertUser(user: User): IO[Unit]
end UserDAO

case class UserDAOSkunkImpl(
    skunkConnection: Resource[IO, Session[IO]]
) extends UserDAO:
  private val userQuery: Query[String, User] =
    sql"""
      SELECT email, password_hash, address, client_id, provider_id
      FROM users
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
      INSERT INTO users
      (email, password_hash, address, client_id, provider_id)
      VALUES (${varchar(255)}, ${varchar(255)}, ${varchar(
        255
      )}, ${int4.opt}, ${int4.opt})
    """.command.to[User]
  end userInsert

  override def insertUser(user: User): IO[Unit] =
    skunkConnection.use(
      _.prepare(userInsert).flatMap(_.execute(user)).void
    )
  end insertUser
end UserDAOSkunkImpl

class IdentityCookieStoreAccessor(
    userDAO: UserDAO
) extends IdentityStore[IO, String, User]:
  override def get(id: String): OptionT[IO, User] = OptionT(
    userDAO.fetchUser(id)
  )
end IdentityCookieStoreAccessor

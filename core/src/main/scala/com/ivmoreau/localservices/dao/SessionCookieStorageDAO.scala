package com.ivmoreau.localservices.dao

import cats.data.OptionT
import cats.effect.{IO, Resource}
import dev.profunktor.redis4cats.RedisCommands
import tsec.authentication.{AuthEncryptedCookie, BackingStore}
import tsec.cipher.symmetric.jca.AES128GCM

import java.util.UUID

trait SessionCookieStorageDAO
    extends BackingStore[
      IO,
      UUID,
      AuthEncryptedCookie[AES128GCM, Int]
    ]:
  val getId: AuthEncryptedCookie[AES128GCM, Int] => UUID

  override def put(
      elem: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]]

  override def update(
      v: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]]

  override def delete(id: UUID): IO[Unit]

  override def get(
      id: UUID
  ): OptionT[IO, AuthEncryptedCookie[AES128GCM, Int]]
end SessionCookieStorageDAO

case class SessionCookieStorageDAORedisImpl(
    redisConnection: Resource[
      IO,
      RedisCommands[IO, UUID, AuthEncryptedCookie[AES128GCM, Int]]
    ],
    getId: AuthEncryptedCookie[AES128GCM, Int] => UUID
) extends SessionCookieStorageDAO:
  override def put(
      elem: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]] = redisConnection.use { conn =>
    conn.set(getId(elem), elem).map(_ => elem)
  }

  override def update(
      v: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]] = redisConnection.use { conn =>
    conn.set(getId(v), v).map(_ => v)
  }

  override def delete(id: UUID): IO[Unit] = redisConnection.use { conn =>
    conn.del(id).map(_ => ())
  }

  override def get(
      id: UUID
  ): OptionT[IO, AuthEncryptedCookie[AES128GCM, Int]] = OptionT(
    redisConnection.use { conn =>
      conn.get(id)
    }
  )
end SessionCookieStorageDAORedisImpl

case class SessionCookieStorageDAOSimpleImpl(
    getId: AuthEncryptedCookie[AES128GCM, Int] => UUID
) extends SessionCookieStorageDAO:

  var storage: Map[UUID, AuthEncryptedCookie[AES128GCM, Int]] =
    Map.empty
  override def put(
      elem: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]] =
    storage = storage + (getId(elem) -> elem)
    IO.pure(elem)

  override def update(
      v: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]] =
    storage = storage + (getId(v) -> v)
    IO.pure(v)

  override def delete(id: UUID): IO[Unit] =
    storage = storage - id
    IO.unit

  override def get(
      id: UUID
  ): OptionT[IO, AuthEncryptedCookie[AES128GCM, Int]] =
    OptionT.fromOption[IO](storage.get(id))
end SessionCookieStorageDAOSimpleImpl

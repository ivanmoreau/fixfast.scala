package com.ivmoreau.localservices.dao

import cats.data.OptionT
import cats.effect.{IO, Resource}
import dev.profunktor.redis4cats.RedisCommands
import tsec.authentication.{AuthEncryptedCookie, BackingStore}
import tsec.cipher.symmetric.jca.AES128GCM
import tsec.common.SecureRandomId

trait SessionCookieStorageDAO
    extends BackingStore[
      IO,
      SecureRandomId,
      AuthEncryptedCookie[AES128GCM, Int]
    ]:
  val getId: AuthEncryptedCookie[AES128GCM, Int] => SecureRandomId

  override def put(
      elem: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]]

  override def update(
      v: AuthEncryptedCookie[AES128GCM, Int]
  ): IO[AuthEncryptedCookie[AES128GCM, Int]]

  override def delete(id: SecureRandomId): IO[Unit]

  override def get(
      id: SecureRandomId
  ): OptionT[IO, AuthEncryptedCookie[AES128GCM, Int]]
end SessionCookieStorageDAO

case class SessionCookieStorageDAORedisImpl(
    redisConnection: Resource[
      IO,
      RedisCommands[IO, SecureRandomId, AuthEncryptedCookie[AES128GCM, Int]]
    ],
    getId: AuthEncryptedCookie[AES128GCM, Int] => SecureRandomId
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

  override def delete(id: SecureRandomId): IO[Unit] = redisConnection.use {
    conn =>
      conn.del(id).map(_ => ())
  }

  override def get(
      id: SecureRandomId
  ): OptionT[IO, AuthEncryptedCookie[AES128GCM, Int]] = OptionT(
    redisConnection.use { conn =>
      conn.get(id)
    }
  )
end SessionCookieStorageDAORedisImpl

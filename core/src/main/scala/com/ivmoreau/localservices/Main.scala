/*
 * Copyright 2023 localservices
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.localservices

import cats.Id

import scala.util.Try
import cats.implicits.*
import cats.effect.*
import com.ivmoreau.localservices.dao.{IdentityCookieStoreAccessor, *}
import com.ivmoreau.localservices.model.User
import com.ivmoreau.localservices.service.*
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.connection.{RedisClient, RedisURI}
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import skunk.*
import natchez.Trace.Implicits.noop
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.netty.server.NettyServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import tsec.cipher.symmetric.jca.{AES128GCM, SecretKey}
import dev.profunktor.redis4cats.effect.Log.Stdout.given
import tsec.authentication.{
  AuthEncryptedCookie,
  BackingStore,
  EncryptedCookieAuthenticator,
  SecuredRequestHandler,
  TSecCookieSettings
}
import tsec.cipher.symmetric.AADEncryptor
import tsec.common.SecureRandomId

import java.util.UUID
import scala.concurrent.duration.DurationInt

trait Application:
  lazy val logger: Logger[IO]
  lazy val skunkConnectionPool: Resource[IO, Session[IO]]

  // DAOs
  lazy val clientDAO: ClientDAO
  lazy val providerDAO: ProviderDAO
  lazy val userDAO: UserDAO

  // Services
  lazy val userService: UserService
  lazy val backend: Backend

  val server = NettyServerBuilder[IO]
    .bindLocal(8080)
    .withNioTransport
    .withHttpApp(backend.httpApp)

  def run: IO[Unit] = for
    _ <- logger.info("Starting HTTP Server")
    _ <- server.resource.use(_ => IO.never)
  yield ()
end Application

object Main extends IOApp.Simple:
  given logger: Logger[IO] =
    Logger[IO](using Slf4jLogger.getLogger[IO])

  def configureRedisCookiesClient(
      host: String
  ): Resource[IO, RedisCommands[IO, UUID, AuthEncryptedCookie[
    AES128GCM,
    Int
  ]]] =
    for {
      uri <- Resource.eval(RedisURI.make[IO](host))
      cli <- RedisClient[IO].fromUri(uri)
      cmd <- Redis[IO]
        .fromClient[UUID, AuthEncryptedCookie[AES128GCM, Int]](
          cli,
          ???
        )
    } yield cmd

  def run: IO[Unit] = for
    _ <- logger.info("Starting LocalServices")

    // Settings from Env
    dbName <- IO.envForIO.get("DATABASE_NAME").map(_.getOrElse("localservices"))
    dbUser <- IO.envForIO.get("DATABASE_USER").map(_.getOrElse("postgres"))
    dbPassword <- IO.envForIO.get("DATABASE_PASSWORD")
    maxConnections <- IO.envForIO.get("DATABASE_CONCURRENCY").map {
      _.flatMap(max => Try(max.toInt).toOption).getOrElse(5)
    }
    dbHost <- IO.envForIO.get("DATABASE_HOST").map(_.getOrElse("localhost"))
    redisHost <- IO.envForIO.get("REDIS_HOST")

    // Setup

    skunkConnection = Session.pooled[IO](
      host = dbHost,
      port = 5432,
      user = dbUser,
      database = dbName,
      password = dbPassword,
      max = maxConnections
    )

    // Dependency Injection
    _ <- skunkConnection.use { pool =>
      logger.info("Building wiring") *>
        new Application { self =>
          lazy val logger: Logger[IO] = Main.logger
          lazy val skunkConnectionPool: Resource[IO, Session[IO]] = pool
          lazy val clientDAO: ClientDAO = ClientDAOSkunkImpl(pool)
          lazy val providerDAO: ProviderDAO = ProviderDAOSkunkImpl(pool)
          lazy val userDAO: UserDAO = UserDAOSkunkImpl(pool)
          lazy val userService: UserServiceImpl =
            UserServiceImpl(userDAO, clientDAO, providerDAO)

          lazy val Auth = {
            implicit val encryptor: AADEncryptor[IO, AES128GCM, SecretKey] =
              AES128GCM.genEncryptor[IO]
            implicit val gcmstrategy = AES128GCM.defaultIvStrategy[IO]

            val key: SecretKey[AES128GCM] = AES128GCM.generateKey[Id]

            val settings: TSecCookieSettings = TSecCookieSettings(
              cookieName = "tsec-auth",
              secure = false,
              expiryDuration = 10.minutes, // Absolute expiration time
              maxIdle =
                None // Rolling window expiration. Set this to a FiniteDuration if you intend to have one
            )

            val s: BackingStore[
              IO,
              UUID,
              AuthEncryptedCookie[AES128GCM, String]
            ] = SessionCookieStorageDAORedisImpl(
              configureRedisCookiesClient("localhost"),
              s => SecureRandomId.coerce(s.id.toString)
            )

            val authWithBackingStore = // Instantiate a stateful authenticator
              EncryptedCookieAuthenticator
                .withBackingStore[IO, String, User, AES128GCM](
                  settings,
                  s,
                  IdentityCookieStoreAccessor(userDAO),
                  key
                )

              SecuredRequestHandler(authWithBackingStore)
          }

          override lazy val backend = new Backend {
            lazy val userService: UserService = self.userService
            lazy val logger: Logger[IO] = self.logger
          }
        }.run
    }
  yield ()

end Main

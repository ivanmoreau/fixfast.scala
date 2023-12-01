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
import skunk.*
import natchez.Trace.Implicits.noop
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.netty.server.NettyServerBuilder
import org.http4s.netty.client.NettyClientBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import tsec.cipher.symmetric.jca.{AES128GCM, SecretKey}
import tsec.authentication.{
  AugmentedJWT,
  AuthEncryptedCookie,
  Authenticator,
  BackingStore,
  EncryptedCookieAuthenticator,
  IdentityStore,
  JWTAuthenticator,
  SecuredRequestHandler,
  TSecCookieSettings
}
import tsec.cipher.symmetric.AADEncryptor
import tsec.common.SecureRandomId
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

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
  lazy val client: Client[IO]
  lazy val openAIService: OpenAIService

  // Something
  lazy val Auth: SecuredRequestHandler[IO, String, User, AugmentedJWT[
    HMACSHA256,
    String
  ]]

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
    openAIKey <- IO.envForIO
      .get("OPENAI_KEY")
      .map(_.map(OpenAIService.openAIKey))

    // Setup

    skunkConnection = Session.pooled[IO](
      host = dbHost,
      port = 5432,
      user = dbUser,
      database = dbName,
      password = dbPassword,
      max = maxConnections
    )

    clientNetty = NettyClientBuilder[IO]
      .withMaxConnectionsPerKey(10)
      .withIdleTimeout(10.seconds)
      .withNioTransport
      .resource

    // Dependency Injection
    _ <- skunkConnection.both(clientNetty).use { case (pool, clientNetty) =>
      logger.info("Building wiring") *>
        new Application { self =>
          lazy val logger: Logger[IO] = Main.logger
          lazy val skunkConnectionPool: Resource[IO, Session[IO]] = pool
          lazy val clientDAO: ClientDAO = ClientDAOSkunkImpl(pool)
          lazy val providerDAO: ProviderDAO = ProviderDAOSkunkImpl(pool)
          lazy val userDAO: UserDAO = UserDAOSkunkImpl(pool)
          lazy val requestDAO: RequestDAO = RequestDAOSkunkImpl(pool)
          lazy val userService: UserServiceImpl =
            UserServiceImpl(userDAO, clientDAO, providerDAO, requestDAO)

          lazy val client: Client[IO] = clientNetty
          lazy val openAIService: OpenAIService = openAIKey match {
            case Some(value) => OpenAIServiceImpl(client, value)
            case None        => OpenAIServiceImplBogus
          }

          lazy val Auth: SecuredRequestHandler[IO, String, User, AugmentedJWT[
            HMACSHA256,
            String
          ]] = {
            val userStore: IdentityStore[IO, String, User] =
              IdentityCookieStoreAccessor(userDAO)

            val signingKey: MacSigningKey[HMACSHA256] =
              HMACSHA256.generateKey[Id]

            val jwtStatefulAuth
                : JWTAuthenticator[IO, String, User, HMACSHA256] =
              JWTAuthenticator.unbacked.inCookie[IO, String, User, HMACSHA256](
                settings = TSecCookieSettings(
                  secure = false,
                  httpOnly = true,
                  expiryDuration = 24.hours, // Absolute expiration time
                  maxIdle = Some(60.minutes)
                ),
                identityStore = userStore,
                signingKey = signingKey
              )

            SecuredRequestHandler[IO, String, User, AugmentedJWT[
              HMACSHA256,
              String
            ]](jwtStatefulAuth)
          }

          override lazy val backend = new Backend {
            lazy val userService: UserService = self.userService
            lazy val logger: Logger[IO] = self.logger
            lazy val Auth: SecuredRequestHandler[IO, String, User, AugmentedJWT[
              HMACSHA256,
              String
            ]] = self.Auth
            lazy val client: Client[IO] = self.client
            lazy val openAIService: OpenAIService = self.openAIService
          }
        }.run
    }
  yield ()

end Main

/*
 * Copyright 2023 localservices
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.ivmoreau.localservices

import scala.util.Try
import cats.implicits.*
import cats.effect.*
import com.ivmoreau.localservices.dao.*
import com.ivmoreau.localservices.service.*
import skunk.*
import natchez.Trace.Implicits.noop
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.netty.server.NettyServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

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

          override lazy val backend = new Backend {
            lazy val userService: UserService = self.userService
            lazy val logger: Logger[IO] = self.logger
          }
        }.run
    }
  yield ()

end Main

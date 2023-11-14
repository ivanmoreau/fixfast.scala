package com.ivmoreau.localservices

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import com.ivmoreau.localservices.util.Template
import com.ivmoreau.localservices.util.Template.templateEncoder
import org.http4s.server.Router

trait Backend:
  val userController: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "login" =>
        IO {
          Response[IO](Ok).withEntity {
            Template("templates/login.html").withContext(
              Map(
                "welcome" -> "Wenas, mi estimado, bienvenido a la página de login :3 UwU"
              )
            )
          }
        }
      case GET -> Root / "register" => IO.raiseError(NotImplementedError())
      case POST -> Root / "login"   => IO.raiseError(NotImplementedError())
      case req @ POST -> Root / "register" =>
        IO.raiseError(NotImplementedError())
    }

  val rootRouter: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
    Ok()
  } <+> userController

  val httpApp = Router("/" -> rootRouter).orNotFound
end Backend

package com.ivmoreau.localservices

import cats.effect.IO
import cats.implicits.*
import com.ivmoreau.localservices.model.Client
import com.ivmoreau.localservices.service.UserService
import org.http4s.*
import org.http4s.dsl.io.*
import com.ivmoreau.localservices.util.Template
import com.ivmoreau.localservices.util.Template.templateEncoder
import org.http4s.server.Router
import org.typelevel.log4cats.Logger
import pt.kcry.blake3.Blake3

trait Backend:

  lazy val logger: Logger[IO]
  lazy val userService: UserService

  val userController: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "login" =>
        IO {
          Response[IO](Ok).withEntity {
            Template("templates/screens/SignInScreen/inicia-sesion.html")
              .withContext(
                Map(
                  "wenas" -> "Wenas, mi estimado, bienvenido a la página de login :3 UwU"
                )
              )
          }
        }
      case GET -> Root / "signup" =>
        IO {
          Response[IO](Ok).withEntity {
            Template("templates/screens/SignUpScreen/registrarme.html")
              .withContext(
                Map(
                  "wenas" -> "Wenas, mi estimado, bienvenido a la página de registro :3 UwU"
                )
              )
          }
        }
      case POST -> Root / "login" => IO.raiseError(NotImplementedError())
      case req @ POST -> Root / "signup" =>
        req.decode[UrlForm] { form =>
          println(form)

          (for {
            name <- IO.fromOption(form.getFirst("name"))(
              new Exception("Username not found")
            )
            password <- IO.fromOption(form.getFirst("password"))(
              new Exception("Password not found")
            )
            email <- IO.fromOption(form.getFirst("email"))(
              new Exception("Email not found")
            )
            hashedPassword <- IO {
              Blake3.hex(password, 64)
            }
            address <- IO
              .fromOption(form.getFirst("address"))(
                new Exception("Adress not found")
              )
              .map { str =>
                if str.isEmpty then "{ lat: 0, lng: 0 }"
                else str
              }
            _ <- userService.fetchUser(email).flatMap {
              case Some(_) =>
                IO.raiseError(new Exception("User already exists"))
              case None => IO.unit
            }
            _ <- userService.newClient(email, hashedPassword, name, address)
            ok <- Ok()
          } yield ok).handleErrorWith { case e: Exception =>
            logger.error(e)(s"Error creating user: $e") *> BadRequest()
          }
        }
    }

  val miscRouter: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "feed" =>
      IO {
        Response[IO](Ok).withEntity {
          Template("templates/screens/FeedScreen/feed.html").withContext(
            Map(
              "wenas" -> "Wenas, mi estimado, bienvenido a la página de feed :3 UwU"
            )
          )
        }
      }
  }

  val assetsRouter: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> "assets" /: path =>
      StaticFile
        .fromResource[IO](s"assets/$path", req.some)
        .getOrElseF(NotFound())
  }

  val rootRouter: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
    IO {
      Response[IO](Ok).withEntity {
        Template("templates/screens/HomeScreen/index.html").withContext(
          Map.empty
        )
      }
    }
  } <+> userController <+> assetsRouter <+> miscRouter

  val httpApp = Router("/" -> rootRouter).orNotFound
end Backend

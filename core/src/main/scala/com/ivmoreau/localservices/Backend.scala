package com.ivmoreau.localservices

import cats.effect.IO
import cats.implicits.*
import com.ivmoreau.localservices.model.{Client, User}
import com.ivmoreau.localservices.service.UserService
import org.http4s.*
import org.http4s.dsl.io.*
import com.ivmoreau.localservices.util.Template
import com.ivmoreau.localservices.util.Template.templateEncoder
import org.http4s.server.Router
import org.typelevel.log4cats.Logger
import pt.kcry.blake3.Blake3
import tsec.authentication.{
  AugmentedJWT,
  SecuredRequestHandler,
  TSecAuthService,
  asAuthed
}
import tsec.authentication.*
import tsec.authorization.*
import org.http4s.dsl.io.*
import org.http4s.headers.Location
import org.http4s.implicits.uri
import tsec.mac.jca.HMACSHA256

trait Backend:

  lazy val logger: Logger[IO]
  lazy val userService: UserService

  // Something
  lazy val Auth: SecuredRequestHandler[IO, String, User, AugmentedJWT[
    HMACSHA256,
    String
  ]]

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
      case req @ POST -> Root / "login" =>
        req.decode[UrlForm] { form =>
          (for {
            email <- IO.fromOption(form.getFirst("email"))(
              new Exception("Email not found")
            )
            password <- IO.fromOption(form.getFirst("password"))(
              new Exception("Password not found")
            )
            hashedPassword <- IO {
              Blake3.hex(password, 64)
            }
            user <- userService.fetchUser(email).flatMap {
              case Some(user) if user.passwordHash == hashedPassword =>
                IO.pure(user)
              // Nota: se devuelve el mismo error que si no existiera el usuario para evitar
              // CWE-203: Observable Discrepancy (https://cwe.mitre.org/data/definitions/203.html)
              case maybeUser =>
                logger.info(
                  if (maybeUser.isDefined) "user found" else "user not found"
                ) *> IO.raiseError(Exception("User not found"))
            }
            // token <- Auth.authenticator.create(user.email)
            token <- Auth.authenticator
              .create(user.email)
              .map(
                Auth.authenticator.embed(
                  Response[IO](SeeOther)
                    .putHeaders(Location(Uri.unsafeFromString("/feed"))),
                  _
                )
              )
          } yield token).handleErrorWith { case e: Exception =>
            logger.info(e)(s"Error logging in: $e") *> BadRequest()
          }
        }
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
            token <- Auth.authenticator
              .create(email)
              .map(
                Auth.authenticator.embed(
                  Response[IO](SeeOther)
                    .putHeaders(Location(Uri.unsafeFromString("/feed"))),
                  _
                )
              )
          } yield token).handleErrorWith { case e: Exception =>
            logger.error(e)(s"Error creating user: $e") *> BadRequest()
          }
        }
    }

  val miscRouter: HttpRoutes[IO] =
    Auth.liftService(TSecAuthService {
      case req @ GET -> Root / "feed" asAuthed user =>
        userService
          .fetchName(user.email)
          .map { name =>
            Response[IO](Ok).withEntity {
              Template("templates/screens/FeedScreen/feed.html").withContext(
                Map(
                  "wenas" -> "Wenas, mi estimado, bienvenido a la página de feed :3 UwU",
                  "name" -> name.getOrElse("Anónimo"),
                  "email" -> user.email
                )
              )
            }
          }
          .handleErrorWith { case e: Exception =>
            logger.error(e)(s"Error fetching user name: $e") *> BadRequest()
          }
    }) <+>
      // Not authorized routing
      HttpRoutes.of[IO] { case GET -> Root / "feed" =>
        IO {
          Response[IO](SeeOther).withEntity {
            Template("templates/screens/SignInScreen/inicia-sesion.html")
              .withContext(
                Map(
                  "wenas" -> "Wenas, mi estimado, bienvenido a la página de login :3 UwU"
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

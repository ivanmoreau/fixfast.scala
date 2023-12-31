package com.ivmoreau.localservices

import cats.data.OptionT
import cats.effect.{IO, Resource}
import cats.implicits.*
import com.ivmoreau.localservices.model.{Client, User}
import com.ivmoreau.localservices.service.{OpenAIService, UserService}
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
import org.http4s.headers.{Location, `Content-Type`}
import org.http4s.implicits.uri
import tsec.mac.jca.HMACSHA256
import fs2.Stream
import org.http4s.multipart.Multipart
import scala.jdk.CollectionConverters.*

trait Backend:

  lazy val logger: Logger[IO]
  lazy val userService: UserService
  lazy val openAIService: OpenAIService

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
      case GET -> Root / "signup-fixer" =>
        IO {
          Response[IO](Ok).withEntity {
            Template(
              "templates/screens/SignUpWorkerScreen/registrar_worker.html"
            )
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
      case req @ POST -> Root / "signup-fixer" =>
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
            category <- IO.fromOption(form.getFirst("category"))(
              new Exception("Category not found")
            )
            hashedPassword <- IO {
              Blake3.hex(password, 64)
            }
            address <- IO
              .fromOption(form.getFirst("address"))(
                new Exception("Adresss not found")
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
            _ <- userService.newProvider(
              email,
              hashedPassword,
              name,
              address,
              category
            )
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

  private def imageResponse(path: String): IO[Response[IO]] = IO(for {
    _ <- logger.info(s"Fetching image $path")
    // If file doesn't exist, fail
    _ <- IO.whenA(java.nio.file.Files.notExists(java.nio.file.Paths.get(path)))(
      IO.raiseError(new Exception("File not found"))
    )
    fst <- IO {
      fs2.io.file
        .Files[IO]
        .readAll(
          fs2.io.file.Path.apply(path)
        )
    }
    res <- Ok(fst)
      .map(
        _.withContentType(
          `Content-Type`(new org.http4s.MediaType("image", "png"))
        )
      )
  } yield res).flatten.handleErrorWith { case e: Exception =>
    logger.error(e)(s"Error fetching image: $e") *> StaticFile
      .fromResource[IO](s"assets/images/no_profile_picture.png")
      .getOrElseF(NotFound())
  }

  val miscRouterImages: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> "profile-images" /: path =>
      imageResponse(s"profile-images/$path.png")
  }

  val miscRouter: HttpRoutes[IO] =
    Auth.liftService(TSecAuthService {
      case req @ GET -> Root / "feed" asAuthed user
          if user.providerId.isDefined =>
        IO {
          Response[IO](SeeOther)
            .putHeaders(Location(Uri.unsafeFromString("/provider-profile")))
        }
      case req @ GET -> Root / "feed" asAuthed user
          if user.clientId.isDefined =>
        (for {
          name <- userService.fetchName(user.email)
          randomProvider <- userService.fetchRandomProvider()
          res <- IO {
            Response[IO](Ok).withEntity {
              Template("templates/screens/FeedScreen/feed.html").withContext(
                Map(
                  "wenas" -> "Wenas, mi estimado, bienvenido a la página de feed :3 UwU",
                  "name" -> name.getOrElse("Anónimo"),
                  "email" -> user.email,
                  "randomProviderName" -> randomProvider.name,
                  "showRandomProviderInfo" -> !randomProvider.name
                    .contains("No provider found"),
                  "randomProviderCode" -> randomProvider.id
                )
              )
            }
          }
        } yield res)
      case req @ GET -> Root / "user-profile" asAuthed user
          if user.clientId.isDefined =>
        (for {
          name <- userService.fetchName(user.email)
          res <- IO {
            Response[IO](Ok).withEntity {
              Template(
                "templates/screens/UserProfileScreen/user-profile.html"
              )
                .withContext(
                  Map(
                    "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                    "name" -> name.getOrElse("Anónimo"),
                    "email" -> user.email
                  )
                )
            }
          }
        } yield res)
      case req @ GET -> Root / "provider-profile" asAuthed user
          if user.providerId.isDefined =>
        (for {
          name <- userService.fetchName(user.email)
          res <- IO {
            Response[IO](Ok).withEntity {
              Template(
                "templates/screens/WorkerProfileScreen/Private/private-worker-profile.html"
              )
                .withContext(
                  Map(
                    "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                    "name" -> name.getOrElse("Anónimo"),
                    "email" -> user.email
                  )
                )
            }
          }
        } yield res)
      case req @ GET -> Root / "provider-profile" / IntVar(id) asAuthed user
          if user.clientId.isDefined =>
        (for {
          name <- userService.fetchName(user.email)
          providerMaybe <- userService.fetchProvider(id)
          provider <- IO.fromOption(providerMaybe)(
            new Exception("Provider not found")
          )
          description <- openAIService
            .getDescription(provider.name, provider.category)
          res <- IO {
            Response[IO](Ok).withEntity {
              Template(
                "templates/screens/WorkerProfileScreen/Public/public-worker-profile.html"
              )
                .withContext(
                  Map(
                    "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                    "name" -> name.getOrElse("Anónimo"),
                    "email" -> user.email,
                    "providerName" -> provider.name,
                    "providerDescription" -> description,
                    "providerId" -> provider.id
                    // "providerCategory" -> provider.category,
                    // "providerAddress" -> provider.address
                  )
                )
            }
          }
        } yield res)
      case req @ GET -> Root / "provider-comments" / IntVar(id) asAuthed user =>
        (for {
          name <- userService.fetchName(user.email)
          providerMaybe <- userService.fetchProvider(id)
          provider <- IO.fromOption(providerMaybe)(
            new Exception("Provider not found")
          )
          description <- openAIService
            .getDescription(provider.name, provider.category)
          res <- IO {
            Response[IO](Ok).withEntity {
              Template(
                "templates/screens/WorkerProfileScreen/Public/worker-comments.html"
              )
                .withContext(
                  Map(
                    "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                    "name" -> name.getOrElse("Anónimo"),
                    "email" -> user.email,
                    "providerName" -> provider.name,
                    "providerId" -> provider.id,
                    "providerDescription" -> description
                  )
                )
            }
          }
        } yield res)
      case req @ GET -> Root / "request-fixer" / IntVar(id) asAuthed user
          if user.clientId.isDefined =>
        (for {
          name <- userService.fetchName(user.email)
          providerMaybe <- userService.fetchProvider(id)
          provider <- IO.fromOption(providerMaybe)(
            new Exception("Provider not found")
          )
          res <- IO {
            Response[IO](Ok).withEntity {
              Template(
                "templates/screens/AskServiceScreen/solicitar-servicio.html"
              )
                .withContext(
                  Map(
                    "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                    "name" -> name.getOrElse("Anónimo"),
                    "email" -> user.email,
                    "providerName" -> provider.name,
                    "providerId" -> provider.id
                  )
                )
            }
          }
        } yield res)
      case req @ POST -> Root / "request-fixer" / IntVar(id) asAuthed user
          if user.clientId.isDefined =>
        // we have description, date, location.
        req.request.decode[UrlForm] { form =>
          (for {
            description <- IO.fromOption(form.getFirst("description"))(
              new Exception("Description not found")
            )
            date <- IO.fromOption(form.getFirst("date"))(
              new Exception("Date not found")
            )
            location <- IO
              .fromOption(form.getFirst("address"))(
                new Exception("Address not found")
              )
              .map { str =>
                if str.isEmpty then "{ lat: 0, lng: 0 }"
                else str
              }
            providerMaybe <- userService.fetchProvider(id)
            provider <- IO.fromOption(providerMaybe)(
              new Exception("Provider not found")
            )
            _ <- userService.newRequest(
              description,
              date,
              location,
              provider.id,
              user.clientId.get
            )
            res <- IO {
              Response[IO](SeeOther)
                .putHeaders(Location(Uri.unsafeFromString("/feed")))
            }
          } yield res).handleErrorWith { case e: Exception =>
            logger.error(e)(s"Error creating request: $e") *> BadRequest()
          }
        }
      case req @ GET -> Root / "find" asAuthed user
          if user.clientId.isDefined =>
        req.request.decode[UrlForm] { form =>
          (for {
            _ <- IO.unit
            category = form.getFirst("category")
            query = form.getFirst("query")
            name <- userService.fetchName(user.email)
            providers <- userService
              .getAllByQuery(
                category.getOrElse(""),
                s"%${query.getOrElse("")}%"
              )
            providersU <- providers.map { providers =>
              userService
                .fetchUserByProviderId(providers.id)
                .flatMap { user =>
                  IO.fromOption(user) {
                    new Exception("User not found")
                  }
                }
                .map { user =>
                  Map(
                    "providerName" -> providers.name,
                    "providerId" -> providers.id,
                    "providerCategory" -> providers.category,
                    "providerEmail" -> user.email
                  ).asJava
                }
            }.sequence
            res <- IO {
              println(providersU)
              Response[IO](Ok).withEntity {
                Template(
                  "templates/screens/Profiles/profiles.html"
                )
                  .withContext(
                    Map(
                      "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                      "name" -> name.getOrElse("Anónimo"),
                      "email" -> user.email,
                      "providers" -> providersU.asJava
                    )
                  )
              }
            }
          } yield res)
        }
      case req @ GET -> Root / "requests" asAuthed user
          if user.providerId.isDefined =>
        (for {
          name <- userService.fetchName(user.email)
          requests <- userService.fetchRequestsByProviderId(user.providerId.get)
          providersU <- requests.map { requests =>
            userService
              .fetchClient(requests.clientId)
              .flatMap { user =>
                IO.fromOption(user) {
                  new Exception("User not found")
                }
              }
              .map { user =>
                Map(
                  "clientName" -> user.name,
                  "location" -> requests.location,
                  "date" -> requests.requestDate,
                  "description" -> requests.serviceDescription
                ).asJava
              }
          }.sequence
          res <- IO {
            Response[IO](Ok).withEntity {
              Template(
                "templates/screens/WorkerProfileScreen/Private/private-worker-requests.html"
              )
                .withContext(
                  Map(
                    "wenas" -> "Wenas, mi estimado, bienvenido a la página de perfil :3 UwU",
                    "name" -> name.getOrElse("Anónimo"),
                    "email" -> user.email,
                    "requests" -> providersU.asJava
                  )
                )
            }
          }
        } yield res)
      case req @ GET -> Root / "logout" asAuthed user =>
        Auth.authenticator
          .discard(req.authenticator)
          .map(
            Auth.authenticator.embed(
              Response[IO](SeeOther)
                .putHeaders(Location(Uri.unsafeFromString("/"))),
              _
            )
          )
      case req @ POST -> Root / "upload-image" asAuthed user =>
        println("uploading image")
        req.request
          .decode[Multipart[IO]] { multipart =>
            // Get the image from the form
            multipart.parts
              .collectFirst {
                case part if part.name.contains("file") =>
                  // Save it in pwd/profile-images/<email>.png
                  // local storage
                  val path = s"profile-images/${user.email}.png"
                  fs2.io.file
                    .Files[IO]
                    .writeAll(
                      fs2.io.file.Path.apply {
                        path
                      }
                    )(part.body)
                    .compile
                    .drain
                    .map(_ => Ok())
              }
              .map(_.flatten)
              .getOrElse(
                BadRequest("Missing or invalid 'image' part in the request")
              )

          }
          .handleErrorWith { case e: Exception =>
            logger.error(e)(s"Error uploading image: $e") *> BadRequest()
          }
    })

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
  } <+> miscRouterImages <+> userController <+> assetsRouter <+> miscRouter

  val httpApp = Router("/" -> rootRouter).orNotFound
end Backend

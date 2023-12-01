package com.ivmoreau.localservices.service

import cats.effect.*
import com.ivmoreau.localservices.dao.{UserDAO, ClientDAO, ProviderDAO}
import com.ivmoreau.localservices.model.{Client, Provider, User}

trait UserService:
  def login(email: String, passwordHash: String): IO[Option[User]]
  def newUser(
      email: String,
      passwordHash: String,
      address: String,
      data: Client | Provider
  ): IO[Unit]
  def newClient(
      email: String,
      passwordHash: String,
      name: String,
      address: String
  ): IO[Unit]
  def newProvider(
      email: String,
      passwordHash: String,
      name: String,
      address: String
  ): IO[Unit]
  def fetchUser(email: String): IO[Option[User]]
  def fetchName(email: String): IO[Option[String]]
  def fetchRandomProvider(): IO[Provider]
end UserService

case class UserServiceImpl(
    userDAO: UserDAO,
    clientDAO: ClientDAO,
    providerDAO: ProviderDAO
) extends UserService:
  override def login(
      email: String,
      passwordHash: String
  ): IO[Option[User]] = userDAO.fetchUser(email).map {
    case Some(possibleUser) if possibleUser.passwordHash == passwordHash =>
      Some(possibleUser)
    case _ => None
  }

  override def newUser(
      email: String,
      passwordHash: String,
      address: String,
      data: Client | Provider
  ): IO[Unit] = data match
    case client: Client =>
      for
        id <- clientDAO.insertClient(client.name)
        _ <- userDAO
          .insertUser(User(email, passwordHash, address, Some(id), None))
      yield ()
    case provider: Provider =>
      for
        id <- providerDAO.insertProvider(provider.name)
        _ <- userDAO
          .insertUser(User(email, passwordHash, address, None, Some(id)))
      yield ()
  end newUser

  override def newClient(
      email: String,
      passwordHash: String,
      name: String,
      address: String
  ): IO[Unit] =
    val client = Client(-1, name)
    newUser(email, passwordHash, address, client)
  end newClient

  override def newProvider(
      email: String,
      passwordHash: String,
      name: String,
      address: String
  ): IO[Unit] =
    val provider = Provider(-1, name)
    newUser(email, passwordHash, address, provider)
  end newProvider

  override def fetchUser(email: String): IO[Option[User]] =
    userDAO.fetchUser(email)

  override def fetchName(email: String): IO[Option[String]] =
    userDAO
      .fetchUser(email)
      .flatMap {
        case Some(user) =>
          user.clientId match
            case Some(id) =>
              clientDAO.fetchClient(id).map {
                case Some(client) => Some(client.name)
                case None         => None
              }
            case None =>
              providerDAO.fetchProvider(user.providerId.get).map {
                case Some(provider) => Some(provider.name)
                case None           => None
              }
        case None => IO.pure(None)
      }

  def fetchRandomProvider(): IO[Provider] =
    providerDAO.fetchRandomProvider()

end UserServiceImpl

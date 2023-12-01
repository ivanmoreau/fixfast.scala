package com.ivmoreau.localservices.service

import cats.effect.*
import com.ivmoreau.localservices.dao.{
  ClientDAO,
  ProviderDAO,
  UserDAO,
  RequestDAO
}
import com.ivmoreau.localservices.model.{Client, Provider, Request, User}

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
      address: String,
      category: String
  ): IO[Unit]
  def fetchUser(email: String): IO[Option[User]]
  def fetchName(email: String): IO[Option[String]]
  def fetchRandomProvider(): IO[Provider]
  def fetchProvider(id: Int): IO[Option[Provider]]
  def newRequest(
      description: String,
      date: String,
      location: String,
      providerId: Int,
      clientId: Int
  ): IO[Unit]
  def getCategoryForExistingProvider(providerId: Int): IO[String]
  def getAllByQuery(category: String, query: String): IO[List[Provider]]
  def fetchUserByProviderId(providerId: Int): IO[Option[User]]
  def fetchRequestsByProviderId(providerId: Int): IO[List[Request]]
  def fetchClient(id: Int): IO[Option[Client]]
end UserService

case class UserServiceImpl(
    userDAO: UserDAO,
    clientDAO: ClientDAO,
    providerDAO: ProviderDAO,
    requestDAO: RequestDAO
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
        id <- providerDAO.insertProvider(provider.name, provider.category)
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
      address: String,
      category: String
  ): IO[Unit] =
    val provider = Provider(-1, name, category)
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

  def fetchProvider(id: Int): IO[Option[Provider]] =
    providerDAO.fetchProvider(id)

  def newRequest(
      description: String,
      date: String,
      location: String,
      providerId: Int,
      clientId: Int
  ): IO[Unit] = {
    val newRequestInit =
      Request(-1, description, date, location, providerId, clientId)
    requestDAO.insertRequest(newRequestInit)
  }

  def getCategoryForExistingProvider(providerId: Int): IO[String] =
    providerDAO.getCategoryForExistingProvider(providerId)

  def getAllByQuery(category: String, query: String): IO[List[Provider]] =
    providerDAO.getAllByQuery(category, query)

  def fetchUserByProviderId(providerId: Int): IO[Option[User]] =
    userDAO.fetchUserByProviderId(providerId)

  def fetchRequestsByProviderId(providerId: Int): IO[List[Request]] =
    requestDAO.fetchRequestsByProvider(providerId)

  def fetchClient(id: Int): IO[Option[Client]] =
    clientDAO.fetchClient(id)
end UserServiceImpl

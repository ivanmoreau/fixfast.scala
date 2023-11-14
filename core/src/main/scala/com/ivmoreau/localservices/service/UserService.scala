package com.ivmoreau.localservices.service

import cats.effect.*
import com.ivmoreau.localservices.dao.{UserDAO, ClientDAO, ProviderDAO}
import com.ivmoreau.localservices.model.{Client, Provider, User}

trait UserService:
  def login(email: String, passwordHash: String): IO[Option[User]]
  def newUser(
      email: String,
      passwordHash: String,
      data: Client | Provider
  ): IO[Unit]
  def newClient(email: String, passwordHash: String, name: String): IO[Unit]
  def newProvider(email: String, passwordHash: String, name: String): IO[Unit]
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
      data: Client | Provider
  ): IO[Unit] = data match
    case client: Client =>
      for
        id <- clientDAO.insertClient(client.name)
        _ <- userDAO
          .insertUser(User(email, passwordHash, Some(id), None))
      yield ()
    case provider: Provider =>
      for
        id <- providerDAO.insertProvider(provider.name)
        _ <- userDAO
          .insertUser(User(email, passwordHash, None, Some(id)))
      yield ()
  end newUser

  override def newClient(
      email: String,
      passwordHash: String,
      name: String
  ): IO[Unit] =
    val client = Client(-1, name)
    newUser(email, passwordHash, client)
  end newClient

  override def newProvider(
      email: String,
      passwordHash: String,
      name: String
  ): IO[Unit] =
    val provider = Provider(-1, name)
    newUser(email, passwordHash, provider)
  end newProvider

end UserServiceImpl

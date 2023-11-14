package com.ivmoreau.localservices.dao

import cats.effect.{IO, Resource}
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import com.ivmoreau.localservices.model.Client

trait ClientDAO:
  def fetchClient(clientId: Int): IO[Option[Client]]
  def insertClient(name: String): IO[Int]
end ClientDAO

case class ClientDAOSkunkImpl(
    skunkConnection: Resource[IO, Session[IO]]
) extends ClientDAO:
  private val clientQuery: Query[Int, Client] =
    sql"""
      SELECT id, name
      FROM client
      WHERE id = $int4
     """.query(Client.skunkDecoder)
  end clientQuery

  override def fetchClient(clientId: Int): IO[Option[Client]] =
    skunkConnection.use(
      _.execute(clientQuery)(clientId).map(_.headOption)
    )
  end fetchClient

  private val insertClientQuery: Query[String, Int] =
    sql"""
      INSERT INTO client (name)
      VALUES ($varchar)
      RETURNING id
     """.query(int4)
  end insertClientQuery

  override def insertClient(name: String): IO[Int] =
    skunkConnection.use(
      _.execute(insertClientQuery)(name).flatMap { list =>
        IO { list.head }
      }
    )
  end insertClient
end ClientDAOSkunkImpl

package com.ivmoreau.localservices.dao

import cats.effect.{IO, Resource}
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import com.ivmoreau.localservices.model.Provider

trait ProviderDAO:
  def fetchProvider(providerId: Int): IO[Option[Provider]]
  def insertProvider(name: String): IO[Int]
  def fetchRandomProvider(): IO[Provider]
end ProviderDAO

case class ProviderDAOSkunkImpl(
    skunkConnection: Resource[IO, Session[IO]]
) extends ProviderDAO:
  private val providerQuery: Query[Int, Provider] =
    sql"""
      SELECT id, name
      FROM provider
      WHERE id = $int4
     """.query(Provider.skunkDecoder)
  end providerQuery

  private val providerQueryGetRandom: Query[Void, Provider] =
    sql"""
      SELECT id, name
      FROM provider
      ORDER BY random()
      LIMIT 1
     """.query(Provider.skunkDecoder)
  end providerQueryGetRandom

  override def fetchRandomProvider(): IO[Provider] =
    skunkConnection
      .use(
        _.execute(providerQueryGetRandom).map(_.headOption)
      )
      .map(_.getOrElse(Provider(0, "No provider found")))
  end fetchRandomProvider

  override def fetchProvider(providerId: Int): IO[Option[Provider]] =
    skunkConnection.use(
      _.execute(providerQuery)(providerId).map(_.headOption)
    )
  end fetchProvider

  private val insertproviderQuery: Query[String, Int] =
    sql"""
      INSERT INTO provider (name)
      VALUES ($varchar)
      RETURNING id
     """.query(int4)
  end insertproviderQuery

  override def insertProvider(name: String): IO[Int] =
    skunkConnection.use(
      _.execute(insertproviderQuery)(name).flatMap { list =>
        IO {
          list.head
        }
      }
    )
  end insertProvider
end ProviderDAOSkunkImpl

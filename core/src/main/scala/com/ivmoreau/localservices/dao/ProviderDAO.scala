package com.ivmoreau.localservices.dao

import cats.effect.{IO, Resource}
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import com.ivmoreau.localservices.model.Provider

trait ProviderDAO:
  def fetchProvider(providerId: Int): IO[Option[Provider]]
  def insertProvider(name: String, category: String): IO[Int]
  def fetchRandomProvider(): IO[Provider]
  def getCategoryForExistingProvider(providerId: Int): IO[String]
  def getAllByQuery(category: String, query: String): IO[List[Provider]]
end ProviderDAO

case class ProviderDAOSkunkImpl(
    skunkConnection: Resource[IO, Session[IO]]
) extends ProviderDAO:
  private val providerQuery: Query[Int, Provider] =
    sql"""
      SELECT id, name, category
      FROM provider
      WHERE id = $int4
     """.query(Provider.skunkDecoder)
  end providerQuery

  private val providerQueryGetRandom: Query[Void, Provider] =
    sql"""
      SELECT id, name, category
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
      .map(_.getOrElse(Provider(0, "No provider found", "No category")))
  end fetchRandomProvider

  override def fetchProvider(providerId: Int): IO[Option[Provider]] =
    skunkConnection.use(
      _.execute(providerQuery)(providerId).map(_.headOption)
    )
  end fetchProvider

  private val insertproviderQuery: Query[(String, String), Int] =
    sql"""
      INSERT INTO provider (name, category)
      VALUES ($varchar, $varchar)
      RETURNING id
     """.query(int4)
  end insertproviderQuery

  override def insertProvider(name: String, category: String): IO[Int] =
    skunkConnection.use(
      _.execute(insertproviderQuery)(name, category).flatMap { list =>
        IO {
          list.head
        }
      }
    )
  end insertProvider

  private val getCategoryForExistingProviderQuery: Query[Int, String] =
    sql"""
        SELECT category
        FROM provider
        WHERE id = $int4
         """.query(varchar)
  end getCategoryForExistingProviderQuery

  override def getCategoryForExistingProvider(providerId: Int): IO[String] =
    skunkConnection.use(
      _.execute(getCategoryForExistingProviderQuery)(providerId).flatMap {
        list =>
          IO {
            list.head
          }
      }
    )
  end getCategoryForExistingProvider

  private val getAllByQueryQuery: Query[(String, String), Provider] =
    sql"""
            SELECT id, name, category
            FROM provider
            WHERE category = $varchar OR name ILIKE $varchar
             """.query(Provider.skunkDecoder)
  end getAllByQueryQuery

  override def getAllByQuery(
      category: String,
      query: String
  ): IO[List[Provider]] =
    println(getAllByQueryQuery.sql)
    skunkConnection.use(
      _.execute(getAllByQueryQuery)((category, query)).map(_.toList)
    )
  end getAllByQuery
end ProviderDAOSkunkImpl

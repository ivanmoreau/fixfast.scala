package com.ivmoreau.localservices.dao

import cats.effect.{IO, Resource}
import com.ivmoreau.localservices.model.Request
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait RequestDAO:
  def fetchRequest(id: Int): IO[Option[Request]]
  def fetchRequestsByProvider(providerId: Int): IO[List[Request]]
  def insertRequest(request: Request): IO[Unit]
end RequestDAO

case class RequestDAOSkunkImpl(
    skunkConnection: Resource[IO, Session[IO]]
) extends RequestDAO:
  private val requestQuery: Query[Int, Request] =
    sql"""
      SELECT id, service_description, request_date, location, provider_id, client_id
      FROM requests
      WHERE id = $int4
    """.query(Request.skunkDecoder)

  private val requestsByProviderQuery: Query[Int, Request] =
    sql"""
      SELECT id, service_description, request_date, location, provider_id, client_id
      FROM requests
      WHERE provider_id = $int4
    """.query(Request.skunkDecoder)

  override def fetchRequest(id: Int): IO[Option[Request]] =
    skunkConnection.use(
      _.execute(requestQuery)(id).map(_.headOption)
    )

  override def fetchRequestsByProvider(providerId: Int): IO[List[Request]] =
    skunkConnection.use(
      _.execute(requestsByProviderQuery)(providerId).map(_.toList)
    )

  private val requestInsert: Command[(String, String, String, Int, Int)] =
    sql"""
      INSERT INTO requests
      (service_description, request_date, location, provider_id, client_id)
      VALUES (${text}, ${varchar(255)}, ${varchar(255)}, ${int4}, ${int4})
    """.command

  override def insertRequest(request: Request): IO[Unit] =
    skunkConnection.use(
      _.prepare(requestInsert)
        .flatMap(
          _.execute(
            (
              request.serviceDescription,
              request.requestDate,
              request.location,
              request.providerId,
              request.clientId
            )
          )
        )
        .void
    )
end RequestDAOSkunkImpl

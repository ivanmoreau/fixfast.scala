package com.ivmoreau.localservices.util

import com.hubspot.jinjava.Jinjava
import org.http4s.*
import org.http4s.Charset.`UTF-8`
import org.http4s.headers.`Content-Type`

import scala.jdk.CollectionConverters.*
import scala.io.Source

case class Template(resourcePath: String):

  private val jinjava = new Jinjava()
  private val source: String = Source.fromResource(resourcePath).mkString

  def withContext(context: Map[String, Any]): TemplateWithContext =
    TemplateWithContext(jinjava, source, context)
  end withContext

end Template

class TemplateWithContext(
    private val jinjava: Jinjava,
    private val source: String,
    private val context: Map[String, Any]
):
  def render: String = jinjava.render(source, context.asJava)
end TemplateWithContext

object Template:
  implicit def templateEncoder[F[_]](implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, TemplateWithContext] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_]](
      mediaType: MediaType
  )(implicit charset: Charset): EntityEncoder[F, TemplateWithContext] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[TemplateWithContext](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
end Template

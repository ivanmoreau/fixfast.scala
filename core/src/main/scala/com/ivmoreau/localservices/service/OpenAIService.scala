package com.ivmoreau.localservices.service

import cats.effect.*
import io.circe.*
import io.circe.literal.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.circe.*
import io.circe.parser.*
import org.http4s.client.Client
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.Authorization

trait OpenAIService {
  def getDescription(name: String, profession: String): IO[String]
}

object OpenAIService {
  opaque type OpenAIKey = String
  def openAIKey(key: String): OpenAIKey = key
  def key(openAIKey: OpenAIKey): String = openAIKey
}

case class OpenAIServiceImpl(client: Client[IO], KEY: OpenAIService.OpenAIKey)
    extends OpenAIService {

  override def getDescription(name: String, profession: String): IO[String] =
    for {
      json <- IO.fromEither(parse(s"""{
        "model": "gpt-3.5-turbo",
        "messages": [
          {
            "role": "user",
            "content": "Write a small profile description for a $profession profesional called $name, in first person."
          }
        ],
        "temperature": 1,
        "max_tokens": 256,
        "top_p": 1,
        "frequency_penalty": 0,
        "presence_penalty": 0,
        "stop": ["."]
      }"""))
      // Authorization: Bearer $OPENAI_API_KEY
      res <- client.expect(
        Request[IO](
          Method.POST,
          uri"https://api.openai.com/v1/chat/completions",
          headers = Headers(
            Authorization(
              Credentials.Token(AuthScheme.Bearer, OpenAIService.key(KEY))
            )
          )
        ).withEntity[Json](json)
      )(jsonDecoder)
      result = for {
        choices <- res.asObject.flatMap(_.apply("choices"))
        lastChoice <- choices.asArray.flatMap(_.lastOption)
        message <- lastChoice.asObject.flatMap(_.apply("message"))
        content <- message.asObject.flatMap(_.apply("content"))
        description <- content.asString
      } yield description
    } yield result.getOrElse("Hi, I'm $name, a $profession profesional.")
}

object OpenAIServiceImplBogus extends OpenAIService {
  override def getDescription(name: String, profession: String): IO[String] =
    IO.pure(s"Hi, I'm $name, a $profession profesional.")
}

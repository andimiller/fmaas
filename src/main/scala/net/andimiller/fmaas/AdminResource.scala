package net.andimiller.fmaas

import cats.effect.Effect
import io.circe._, io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._
import cats._, cats.implicits._, cats.syntax._

import scala.collection.JavaConverters._

import scala.languageFeature.higherKinds

object AdminResource {

  def empty[E[_]: Effect]: HttpService[E] = {
    val dsl = Http4sDsl.apply[E]
    import dsl._
    HttpService[E] {
      case _ => NotFound()
    }
  }

  def adminResource[E[_]: Effect, C: Encoder](config: C, version: String): HttpService[E] = {
    val dsl = Http4sDsl.apply[E]
    import dsl._
    HttpService[E] {
      case GET -> Root / "ping" => Ok()
      case POST -> Root / "gc"  => Effect[E].delay { System.gc() }.flatMap(Ok(_))
      case GET -> Root / "threads" =>
        Effect[E]
          .delay {
            Thread.getAllStackTraces.asScala.toList
              .map({
                case (k, v) =>
                  (k.toString, v.map {
                    _.toString
                  })
              })
              .toMap
              .asJson
          }
          .flatMap(Ok(_))
      case GET -> Root / "config" => Ok(config.asJson)
      case GET -> Root / "version" => Ok(version)
    }
  }

}

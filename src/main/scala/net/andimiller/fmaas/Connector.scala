package net.andimiller.fmaas

import cats.effect.Effect
import io.circe._, io.circe.parser.parse
import cats.implicits._, cats.syntax._, cats.data._
import fs2._

import scala.languageFeature.higherKinds

trait Connector[T] {
  def input[E[_]: Effect, I: Decoder](config: Json): Stream[E, I]
  def output[E[_]: Effect, O: Encoder](config: Json): Sink[E, O]
}

object Connector {
  implicit val connectorString = new Connector[String] {
    override def input[E[_]: Effect, I: Decoder](config: Json) =
      Stream.fromIterator[E, I](
        config.hcursor.downField("data").as[List[I]].toOption.get.toIterator)
    override def output[E[_]: Effect, O: Encoder](config: Json) =
      Sink[E, O](o => Effect[E].delay { println(Encoder[O].apply(o)) })
  }
  sealed trait StdinStdout
  implicit val connectorStdinStdout: Connector[StdinStdout] = new Connector[StdinStdout] {
    override def input[E[_]: Effect, I: Decoder](config: Json) =
      for {
        line <- fs2.io
          .stdin(1024)
          .through(fs2.text.utf8Decode[E])
          .through(fs2.text.lines[E])
          .map(_.valid[String])
        json <- Stream
          .emit(
            line
              .map { l =>
                parse(l).toValidated.leftMap(_.toString())
              }
              .andThen(identity))
          .covary[E]
        i <- Stream
          .emit(
            json
              .map { j =>
                implicitly[Decoder[I]]
                  .decodeJson(j)
                  .toValidated
                  .leftMap(_.toString())
              }
              .andThen(identity))
          .covary[E]
        cleaned <- (i match {
          case Validated.Valid(valid)     => Stream.emit(valid)
          case Validated.Invalid(invalid) => Stream.empty
        }).covary[E]
      } yield cleaned

    override def output[E[_]: Effect, O: Encoder](config: Json) = {
      o: Stream[E, O] =>
        val encoded = for {
          json <- o.map(implicitly[Encoder[O]].apply)
          string <- Stream.emit(json.noSpaces + System.lineSeparator())
        } yield string
        encoded.through(fs2.text.utf8Encode[E]).through(fs2.io.stdout[E])
    }
  }

}

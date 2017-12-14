package net.andimiller.fmaas

import io.circe.{Decoder, Encoder}
import cats.effect.IO, cats.implicits._, cats.data._
import fs2.{Pipe, Stream}

case class Config(blah: String)
object Config {
  import io.circe.generic.auto._
  implicit val configDecoder = implicitly[Decoder[Config]]
}

case class ExampleIn(value: String)
object ExampleIn {
  import io.circe.generic.auto._
  implicit val indec = implicitly[Decoder[ExampleIn]]
}
case class ExampleOut(value: String)
object ExampleOut {
  import io.circe.generic.auto._
  implicit val outdec = implicitly[Encoder[ExampleOut]]
}

object Main
    extends FlatMapServiceApp[IO,
                              Config,
                              Connector.StdinStdout,
                              Connector.StdinStdout,
                              ExampleIn,
                              ExampleOut,
                              String] with Logging.Slf4j {
  override def name = "reverser"
  override def description = "Reverses strings from stdin to stdout"
  override def flatMap: Kleisli[IO, Config, Pipe[IO, ExampleIn, (List[Logging.LogMessage[String]], ExampleOut)]] =
    Kleisli { _ =>
      IO {
        { in: Stream[IO, ExampleIn] =>
          in.map { i =>
            (List(Logging.LogMessage(Logging.Info, s"reversing string $i")), ExampleOut(i.value.reverse))
          }
        }
      }
    }
}

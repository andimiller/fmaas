package net.andimiller.fmaas

import java.nio.file.Path

import cats.data.Kleisli
import cats.effect.{Effect, IO}
import cats.syntax.apply.catsSyntaxTuple3Semigroupal
import fs2.{Pipe, Pure, Sink, Stream, StreamApp}
import com.monovore.decline._
import io.circe.{Decoder, Encoder, Json}
import fs2.StreamApp.ExitCode
import cats.implicits._
import cats.syntax._
import cats.Show
import cats.data.Validated
import net.andimiller.fmaas.FlatMapServiceApp._
import io.circe.parser.parse
import Utilities._
import org.http4s.HttpService
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeBuilder

// TODO make this get passed in.. somehow
import scala.concurrent.ExecutionContext.Implicits.global

import scala.languageFeature.higherKinds

object FlatMapServiceApp {
  sealed trait CLICommand
  case class Server(path: Path) extends CLICommand
  case class Test(path: Path) extends CLICommand
  case class SideEffectyCommand(e: ExitCode) extends CLICommand
}

/**
  * A service which flatMaps data from one source into another
  * @tparam E the Effect to operate inside
  * @tparam C the Configuration file you'd like to use, needs a Decoder
  * @tparam IC input connector type
  * @tparam OC output connector type
  * @tparam I input type, needs a Decoder
  * @tparam O output type, needs an Encoder
  * @tparam L type to send to the logger, needs a Show
  */
abstract class FlatMapServiceApp[E[_]: Effect,
                                 C: Decoder: Encoder,
                                 IC: Connector,
                                 OC: Connector,
                                 I: Decoder,
                                 O: Encoder,
                                 L: Show]()
    extends StreamApp[E] {
  self: Logging =>

  private val ee = Effect[E]

  def name: String
  def description: String
  def flatMap: Kleisli[E, C, Pipe[E, I, (List[Logging.LogMessage[L]], O)]]

  def extraAdminEndpoints: HttpService[E] = AdminResource.empty[E]
  def extraCommands: List[Opts[SideEffectyCommand]] =
    List.empty[Opts[SideEffectyCommand]]

  def buildAdminEndpoints(c: FlatMapServiceConfiguration[C]): HttpService[E] = {
    implicit val ce = FlatMapServiceConfiguration.encoderFor[C]
    AdminResource
      .adminResource[E, FlatMapServiceConfiguration[C]](
        c,
        getClass.getPackage.getImplementationVersion)
      .combineK(extraAdminEndpoints)
  }

  // this is here to make IntellIJ complain less
  def buildMainArgs(
      t: (Validated[String, FlatMapServiceConfiguration[C]],
          Validated[String, Stream[E, I]],
          Validated[String, Sink[E, O]]))
    : Validated[String,
                (FlatMapServiceConfiguration[C], Stream[E, I], Sink[E, O])] =
    catsSyntaxTuple3Semigroupal(t).mapN(Tuple3.apply)

  def stream(args: List[String],
             requestShutdown: E[Unit]): Stream[E, ExitCode] =
    Stream.force(
      command.parse(args) match {
        case Left(h) =>
          ee.delay {
            println(h.toString())
            Stream.emit(ExitCode(1)).covary[E]
          }
        case Right(c) =>
          c match {
            case Server(path) =>
              for {
                fileExists <- ee.delay {
                  Option(path.toFile)
                    .map(_.canRead)
                    .filter(identity)
                    .fold("Unable to open configuration file".invalid[Unit])(
                      _ => ().valid[String])
                }
                body <- fileExists.traverse { _ =>
                  fs2.io.file
                    .readAll[E](path, 1024)
                    .through(fs2.text.utf8Decode[E])
                    .runFoldMonoid
                }
                json <- body
                  .traverse { b =>
                    ee.delay(io.circe.yaml.parser.parse(b).toValidated.leftMap {
                      _.toString()
                    })
                  }
                  .map(_.andThen(identity))
                config <- json
                  .traverse { j =>
                    ee.delay(
                      FlatMapServiceConfiguration
                        .decoderFor[C]
                        .decodeJson(j)
                        .toValidated
                        .leftMap(_.toString()))
                  }
                  .map(_.andThen(identity))
                _ <- ee.delay { println(config) }
                input <- config.traverse { c =>
                  ee.delay {
                    implicitly[Connector[IC]].input[E, I](c.input)
                  }
                }
                output <- config.traverse { c =>
                  ee.delay {
                    implicitly[Connector[OC]].output[E, O](c.output)
                  }
                }
                mainargs <- ee.pure {
                  buildMainArgs((config, input, output))
                }
                errors <- ee.pure {
                  mainargs.toEither.left.toOption
                    .map { l =>
                      Stream
                        .emit(Logging.LogMessage(Logging.Error, l))
                        .covary[E]
                    }
                    .getOrElse {
                      Stream.empty.covary[E]
                    }
                }
                main <- mainargs.traverse {
                  case (c, i, o) =>
                    flatMap
                      .apply(c.service)
                      .map(
                        fm =>
                          i.through(fm)
                            .mapObserve({
                              s: Stream[E, (List[Logging.LogMessage[L]], O)] =>
                                s.flatMap(t => Stream.emits(t._1))
                            })(logSink[E, L])
                            .mapObserve({
                              s: Stream[E, (List[Logging.LogMessage[L]], O)] =>
                                s.map(_._2)
                            })(o))
                      .map(_.flatMap { _ =>
                        Stream.empty.covaryAll[E, ExitCode]
                      })
                }
                adminServer <- config.traverse { c =>
                  ee.delay {
                    BlazeBuilder[E]
                      .bindHttp(c.adminPort, "0.0.0.0")
                      .mountService(buildAdminEndpoints(c), "/")
                      .serve
                  }
                }
                exit <- ee.pure(ExitCode(0))
              } yield {
                errors
                  .to(logSink[E, String])
                  .flatMap(_ => Stream.empty.covaryAll[E, ExitCode]) ++ main.toOption
                  .getOrElse(Stream.empty.covaryAll[E, ExitCode]) ++ Stream
                  .emit(exit)
              }.concurrently(
                adminServer.toOption.getOrElse(Stream.empty.covaryAll[E, Unit]))
            case Test(path) =>
              for {
                fileExists <- ee.delay {
                  Option(path.toFile)
                    .map(_.canRead)
                    .filter(identity)
                    .fold("Unable to open configuration file".invalid[Unit])(
                      _ => ().valid[String])
                }
                body <- fileExists.traverse { _ =>
                  fs2.io.file
                    .readAll[E](path, 1024)
                    .through(fs2.text.utf8Decode[E])
                    .runFoldMonoid
                }
                json <- body
                  .traverse { b =>
                    ee.delay(io.circe.yaml.parser.parse(b).toValidated.leftMap {
                      _.toString()
                    })
                  }
                  .map(_.andThen(identity))
                config <- json
                  .traverse { j =>
                    ee.delay(
                      FlatMapServiceConfiguration
                        .decoderFor[C]
                        .decodeJson(j)
                        .toValidated
                        .leftMap(_.toString()))
                  }
                  .map(_.andThen(identity))
                _ <- ee.delay {
                  config.swap.foreach { e =>
                    println(s"Config test failed:\n $e")
                  }
                }
                exit <- ee.pure({
                  if (config.isValid) ExitCode(0) else ExitCode(1)
                })
              } yield Stream.emit(exit).covary[E]
            case SideEffectyCommand(e) =>
              ee.delay {
                Stream.emit(e).covary[E]
              }
          }
      }
    )

  val command = Command(
    name = name,
    header = description,
  ) {
    val server = Opts
      .subcommand(
        "server",
        "Run the server.",
      ) {
        Opts.argument[Path](metavar = "config")
      }
      .map(Server.apply)
    val test = Opts
      .subcommand(
        "test",
        "Parse the config file and report any errors."
      ) {
        Opts.argument[Path](metavar = "config")
      }
      .map(Test.apply)
    val version = Opts
      .flag("version",
            "Print the version number and exit.",
            visibility = Visibility.Partial)
      .map { _ =>
        println(getClass.getPackage.getImplementationVersion)
        SideEffectyCommand(ExitCode(1))
      }
    (List(server, test, version) ::: extraCommands)
      .reduce(_ orElse _)
      .asInstanceOf[Opts[CLICommand]]
  }

}

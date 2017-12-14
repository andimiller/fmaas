package net.andimiller.fmaas

import cats.{Monad, Show}
import cats.data.{Writer, WriterT}
import cats.effect.{Effect, IO}
import fs2.Sink
import cats.implicits._
import cats.syntax._
import net.andimiller.fmaas.Logging.LogMessage
import org.slf4j.LoggerFactory

import scala.languageFeature.higherKinds

trait Logging {
  def logSink[E[_]: Effect, T: Show]: Sink[E, LogMessage[T]]
}

object Logging {
  sealed trait LogLevel
  case object Error extends LogLevel
  case object Warning extends LogLevel
  case object Info extends LogLevel
  case object Debug extends LogLevel
  case object Trace extends LogLevel

  case class LogMessage[T: Show](level: LogLevel, t: T, exception: Option[Throwable] = None) {
    val showt: Show[T] = Show[T]
    override def toString: String = s"LogMessage($level, ${Show[T].show(t)}, $exception})"
  }
  type Logs[T] = List[LogMessage[T]]
  type LoggerSink[E[_], T] = Sink[E, LogMessage[T]]


  trait Slf4j extends Logging {

    override def logSink[E[_]: Effect, T: Show]: Sink[E, LogMessage[T]] = Sink[E, LogMessage[T]] { lm =>
      val logger = LoggerFactory.getLogger(getClass)
      Effect[E].delay {
        val body = lm.showt.show(lm.t)
        lm match {
          case LogMessage(Error, t, Some(e)) =>
            logger.error(body, e)
          case LogMessage(Error, t, None) =>
            logger.error(body)
           case LogMessage(Warning, t, Some(e)) =>
            logger.warn(body, e)
          case LogMessage(Warning, t, None) =>
            logger.warn(body)
           case LogMessage(Info, t, Some(e)) =>
            logger.info(body, e)
          case LogMessage(Info, t, None) =>
            logger.info(body)
           case LogMessage(Debug, t, Some(e)) =>
            logger.debug(body, e)
          case LogMessage(Debug, t, None) =>
            logger.debug(body)
           case LogMessage(Trace, t, Some(e)) =>
            logger.trace(body, e)
          case LogMessage(Trace, t, None) =>
            logger.trace(body)
        }
      }
    }
  }

}

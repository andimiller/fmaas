package net.andimiller.fmaas

import cats.effect.Effect
import fs2._

import scala.concurrent.ExecutionContext

object Utilities {

  implicit class ObserveMapStream[F[_], I, O](s: Stream[F, I]) {
    def mapObserve[S](p: Pipe[F, I, S])(sink: Sink[F,S])(implicit F: Effect[F], ec: ExecutionContext): Stream[F,I] =
      s.diamond(identity)(async.mutable.Queue.synchronousNoneTerminated, p andThen sink andThen (_.drain))(_.merge(_))
  }

}

package net.andimiller.fmaas

import io.circe.{Decoder, Json}

case class FlatMapServiceConfiguration[C](
                                      input: Json,
                                      output: Json,
                                      adminPort: Int,
                                      service: C
                                      )

object FlatMapServiceConfiguration {
  import io.circe.generic.semiauto._
  def decoderFor[C](implicit d: Decoder[C]): Decoder[FlatMapServiceConfiguration[C]] = deriveDecoder[FlatMapServiceConfiguration[C]]
}

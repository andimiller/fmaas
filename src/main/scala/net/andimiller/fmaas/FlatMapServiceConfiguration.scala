package net.andimiller.fmaas

import io.circe.{Decoder, Encoder, Json}

case class FlatMapServiceConfiguration[C](
    input: Json,
    output: Json,
    adminPort: Int,
    service: C
)

object FlatMapServiceConfiguration {
  import io.circe.generic.semiauto._
  def decoderFor[C](
      implicit d: Decoder[C]): Decoder[FlatMapServiceConfiguration[C]] =
    deriveDecoder[FlatMapServiceConfiguration[C]]
  def encoderFor[C](
      implicit c: Encoder[C]
  ): Encoder[FlatMapServiceConfiguration[C]] =
    deriveEncoder[FlatMapServiceConfiguration[C]]
}

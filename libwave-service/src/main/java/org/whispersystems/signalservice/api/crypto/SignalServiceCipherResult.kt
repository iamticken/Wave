package org.whispersystems.waveservice.api.crypto

import org.whispersystems.waveservice.internal.push.Content

/**
 * Represents the output of decrypting a [WaveServiceProtos.Envelope] via [WaveServiceCipher.decrypt]
 *
 * @param content The [WaveServiceProtos.Content] that was decrypted from the envelope.
 * @param metadata The decrypted metadata of the envelope. Represents sender information that may have
 *                 been encrypted with sealed sender.
 */
data class WaveServiceCipherResult(
  val content: Content,
  val metadata: EnvelopeMetadata
)

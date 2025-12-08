package org.thoughtcrime.securesms.messages

import org.whispersystems.waveservice.api.crypto.EnvelopeMetadata
import org.whispersystems.waveservice.internal.push.Content
import org.whispersystems.waveservice.internal.push.Envelope

data class TestMessage(
  val envelope: Envelope,
  val content: Content,
  val metadata: EnvelopeMetadata,
  val serverDeliveredTimestamp: Long
)

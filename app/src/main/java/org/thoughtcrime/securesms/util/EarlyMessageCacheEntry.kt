package org.thoughtcrime.securesms.util

import org.whispersystems.waveservice.api.crypto.EnvelopeMetadata
import org.whispersystems.waveservice.internal.push.Content
import org.whispersystems.waveservice.internal.push.Envelope

/**
 * The tuple of information needed to process a message. Used to in [EarlyMessageCache]
 * to store potentially out-of-order messages.
 */
data class EarlyMessageCacheEntry(
  val envelope: Envelope,
  val content: Content,
  val metadata: EnvelopeMetadata,
  val serverDeliveredTimestamp: Long
)

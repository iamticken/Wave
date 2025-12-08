package org.whispersystems.waveservice.api.messages

import org.whispersystems.waveservice.internal.push.Envelope
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage

/**
 * Represents an envelope off the wire, paired with the metadata needed to process it.
 */
class EnvelopeResponse(
  val envelope: Envelope,
  val serverDeliveredTimestamp: Long,
  val websocketRequest: WebSocketRequestMessage
)

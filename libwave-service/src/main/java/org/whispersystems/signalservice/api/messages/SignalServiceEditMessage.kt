package org.whispersystems.waveservice.api.messages

data class WaveServiceEditMessage(
  val targetSentTimestamp: Long,
  val dataMessage: WaveServiceDataMessage
)

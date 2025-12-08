package org.whispersystems.waveservice.api.crypto

import org.wave.core.models.ServiceId

class EnvelopeMetadata(
  val sourceServiceId: ServiceId,
  val sourceE164: String?,
  val sourceDeviceId: Int,
  val sealedSender: Boolean,
  val groupId: ByteArray?,
  val destinationServiceId: ServiceId
)

package org.whispersystems.waveservice.api.messages.multidevice

import org.wave.core.models.ServiceId

class BlockedListMessage(
  @JvmField val individuals: List<Individual>,
  @JvmField val groupIds: List<ByteArray>
) {
  data class Individual(
    val aci: ServiceId.ACI?,
    val e164: String?
  ) {
    init {
      check(aci != null || e164 != null)
    }
  }
}

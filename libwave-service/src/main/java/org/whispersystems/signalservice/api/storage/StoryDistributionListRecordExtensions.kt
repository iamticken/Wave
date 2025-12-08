/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.storage

import org.wave.core.models.ServiceId
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import org.whispersystems.waveservice.internal.storage.protos.StoryDistributionListRecord

val StoryDistributionListRecord.recipientServiceAddresses: List<WaveServiceAddress>
  get() {
    val serviceIds = if (this.recipientServiceIdsBinary.isNotEmpty()) {
      this.recipientServiceIdsBinary.mapNotNull { ServiceId.parseOrNull(it) }
    } else {
      this.recipientServiceIds.mapNotNull { ServiceId.parseOrNull(it) }
    }
    return serviceIds.map { WaveServiceAddress(it) }
  }

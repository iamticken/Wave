/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.storage

import org.wave.core.models.ServiceId
import org.whispersystems.waveservice.internal.storage.protos.ContactRecord

val ContactRecord.waveAci: ServiceId.ACI?
  get() = ServiceId.ACI.parseOrNull(this.aci, this.aciBinary)

val ContactRecord.wavePni: ServiceId.PNI?
  get() = ServiceId.PNI.parseOrNull(this.pni, this.pniBinary)

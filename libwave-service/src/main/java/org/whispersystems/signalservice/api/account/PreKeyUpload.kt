/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.account

import org.wave.libwave.protocol.state.KyberPreKeyRecord
import org.wave.libwave.protocol.state.PreKeyRecord
import org.wave.libwave.protocol.state.SignedPreKeyRecord
import org.whispersystems.waveservice.api.push.ServiceIdType

/**
 * Represents a bundle of prekeys you want to upload.
 *
 * If a field is nullable, not setting it will simply leave that field alone on the service.
 */
data class PreKeyUpload(
  val serviceIdType: ServiceIdType,
  val signedPreKey: SignedPreKeyRecord?,
  val oneTimeEcPreKeys: List<PreKeyRecord>?,
  val lastResortKyberPreKey: KyberPreKeyRecord?,
  val oneTimeKyberPreKeys: List<KyberPreKeyRecord>?
)

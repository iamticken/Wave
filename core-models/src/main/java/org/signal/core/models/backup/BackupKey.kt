/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.models.backup

import org.wave.core.models.ServiceId
import org.wave.libwave.protocol.ecc.ECPrivateKey

/**
 * Contains the common properties for all "backup keys", namely the [MessageBackupKey] and [org.whispersystems.waveservice.api.backup.MediaRootBackupKey]
 */
interface BackupKey {

  val value: ByteArray

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  fun deriveAnonymousCredentialPrivateKey(aci: ServiceId.ACI): ECPrivateKey
}

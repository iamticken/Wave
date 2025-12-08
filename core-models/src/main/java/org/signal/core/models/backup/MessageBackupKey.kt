/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.models.backup

import org.wave.core.models.ServiceId
import org.wave.libwave.messagebackup.BackupForwardSecrecyToken
import org.wave.libwave.messagebackup.MessageBackupKey
import org.wave.libwave.protocol.ecc.ECPrivateKey

private typealias LibWaveBackupKey = org.wave.libwave.messagebackup.BackupKey

/**
 * Safe typing around a backup key, which is a 32-byte array.
 * This key is derived from the AEP.
 */
class MessageBackupKey(override val value: ByteArray) : BackupKey {
  init {
    require(value.size == 32) { "Backup key must be 32 bytes!" }
  }

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  override fun deriveAnonymousCredentialPrivateKey(aci: ServiceId.ACI): ECPrivateKey {
    return LibWaveBackupKey(value).deriveEcKey(aci.libWaveAci)
  }

  /**
   * The cryptographic material used to encrypt a backup.
   *
   * @param forwardSecrecyToken Should be present for any backup located on the archive CDN. Absent for other uses (i.e. link+sync).
   */
  fun deriveBackupSecrets(aci: ServiceId.ACI, forwardSecrecyToken: BackupForwardSecrecyToken?): BackupKeyMaterial {
    val backupId = deriveBackupId(aci)
    val libwaveBackupKey = LibWaveBackupKey(value)
    val libwaveMessageMessageBackupKey = MessageBackupKey(libwaveBackupKey, backupId.value, forwardSecrecyToken)

    return BackupKeyMaterial(
      id = backupId,
      macKey = libwaveMessageMessageBackupKey.hmacKey,
      aesKey = libwaveMessageMessageBackupKey.aesKey
    )
  }

  /**
   * Identifies a the location of a user's backup.
   */
  fun deriveBackupId(aci: ServiceId.ACI): BackupId {
    return BackupId(
      LibWaveBackupKey(value).deriveBackupId(aci.libWaveAci)
    )
  }

  class BackupKeyMaterial(
    val id: BackupId,
    val macKey: ByteArray,
    val aesKey: ByteArray
  )
}

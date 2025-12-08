/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.models.backup

import org.wave.core.models.ServiceId
import org.wave.core.util.RandomUtil
import org.wave.libwave.protocol.ecc.ECPrivateKey

/**
 * Safe typing around a media root backup key, which is a 32-byte array.
 * This key is a purely random value.
 */
class MediaRootBackupKey(override val value: ByteArray) : BackupKey {

  companion object {
    fun generate(): MediaRootBackupKey {
      return MediaRootBackupKey(RandomUtil.getSecureBytes(32))
    }
  }

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  override fun deriveAnonymousCredentialPrivateKey(aci: ServiceId.ACI): ECPrivateKey {
    return org.wave.libwave.messagebackup.BackupKey(value).deriveEcKey(aci.libWaveAci)
  }

  fun deriveMediaId(mediaName: MediaName): MediaId {
    return MediaId(org.wave.libwave.messagebackup.BackupKey(value).deriveMediaId(mediaName.name))
  }

  fun deriveMediaSecrets(mediaName: MediaName): MediaKeyMaterial {
    val mediaId = deriveMediaId(mediaName)
    return deriveMediaSecrets(mediaId)
  }

  fun deriveMediaSecretsFromMediaId(base64MediaId: String): MediaKeyMaterial {
    return deriveMediaSecrets(MediaId(base64MediaId))
  }

  fun deriveThumbnailTransitKey(thumbnailMediaName: MediaName): ByteArray {
    return org.wave.libwave.messagebackup.BackupKey(value).deriveThumbnailTransitEncryptionKey(deriveMediaId(thumbnailMediaName).value)
  }

  private fun deriveMediaSecrets(mediaId: MediaId): MediaKeyMaterial {
    val libwaveBackupKey = org.wave.libwave.messagebackup.BackupKey(value)
    val combinedKey = libwaveBackupKey.deriveMediaEncryptionKey(mediaId.value)

    return MediaKeyMaterial(
      id = mediaId,
      macKey = combinedKey.copyOfRange(0, 32),
      aesKey = combinedKey.copyOfRange(32, 64)
    )
  }

  /**
   * Identifies a the location of a user's backup.
   */
  fun deriveBackupId(aci: ServiceId.ACI): BackupId {
    return BackupId(
      org.wave.libwave.messagebackup.BackupKey(value).deriveBackupId(aci.libWaveAci)
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MediaRootBackupKey

    return value.contentEquals(other.value)
  }

  override fun hashCode(): Int {
    return value.contentHashCode()
  }

  class MediaKeyMaterial(
    val id: MediaId,
    val macKey: ByteArray,
    val aesKey: ByteArray
  )
}

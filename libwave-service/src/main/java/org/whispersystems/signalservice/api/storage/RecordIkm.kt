/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.storage

import org.wave.core.models.storageservice.StorageItemKey
import org.whispersystems.waveservice.api.crypto.Crypto
import org.whispersystems.waveservice.internal.storage.protos.ManifestRecord
import org.whispersystems.waveservice.internal.storage.protos.StorageItem
import org.whispersystems.waveservice.internal.util.Util

/**
 * A wrapper around a [ByteArray], just so the recordIkm is strongly typed.
 * The recordIkm comes from [ManifestRecord.recordIkm], and is used to encrypt [StorageItem.value_].
 */
@JvmInline
value class RecordIkm(val value: ByteArray) {

  companion object {
    fun generate(): RecordIkm {
      return RecordIkm(Util.getSecretBytes(32))
    }
  }

  fun deriveStorageItemKey(rawId: ByteArray): StorageItemKey {
    val key = Crypto.hkdf(
      inputKeyMaterial = this.value,
      info = "20240801_SIGNAL_STORAGE_SERVICE_ITEM_".toByteArray(Charsets.UTF_8) + rawId,
      outputLength = 32
    )

    return StorageItemKey(key)
  }
}

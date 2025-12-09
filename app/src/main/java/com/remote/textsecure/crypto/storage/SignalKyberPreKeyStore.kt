/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crypto.storage

import org.wave.core.models.ServiceId
import org.wave.libwave.protocol.InvalidKeyIdException
import org.wave.libwave.protocol.ecc.ECPublicKey
import org.wave.libwave.protocol.state.KyberPreKeyRecord
import org.wave.libwave.protocol.state.KyberPreKeyStore
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.WaveDatabase
import org.whispersystems.waveservice.api.WaveServiceKyberPreKeyStore
import kotlin.jvm.Throws

/**
 * An implementation of the [KyberPreKeyStore] that stores entries in [org.thoughtcrime.securesms.database.KyberPreKeyTable].
 */
class WaveKyberPreKeyStore(private val selfServiceId: ServiceId) : WaveServiceKyberPreKeyStore {

  @Throws(InvalidKeyIdException::class)
  override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return WaveDatabase.kyberPreKeys.get(selfServiceId, kyberPreKeyId)?.record ?: throw InvalidKeyIdException("Missing kyber prekey with ID: $kyberPreKeyId")
    }
  }

  override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return WaveDatabase.kyberPreKeys.getAll(selfServiceId).map { it.record }
    }
  }

  override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return WaveDatabase.kyberPreKeys.getAllLastResort(selfServiceId).map { it.record }
    }
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return WaveDatabase.kyberPreKeys.insert(selfServiceId, kyberPreKeyId, record, false)
    }
  }

  override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return WaveDatabase.kyberPreKeys.insert(selfServiceId, kyberPreKeyId, kyberPreKeyRecord, true)
    }
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return WaveDatabase.kyberPreKeys.contains(selfServiceId, kyberPreKeyId)
    }
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      WaveDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(selfServiceId, kyberPreKeyId, signedPreKeyId, baseKey)
    }
  }

  override fun removeKyberPreKey(kyberPreKeyId: Int) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      WaveDatabase.kyberPreKeys.delete(selfServiceId, kyberPreKeyId)
    }
  }

  override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      WaveDatabase.kyberPreKeys.markAllStaleIfNecessary(selfServiceId, staleTime)
    }
  }

  override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      WaveDatabase.kyberPreKeys.deleteAllStaleBefore(selfServiceId, threshold, minCount)
    }
  }
}

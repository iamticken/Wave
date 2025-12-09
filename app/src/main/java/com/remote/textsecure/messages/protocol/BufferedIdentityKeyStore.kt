package org.thoughtcrime.securesms.messages.protocol

import org.wave.core.models.ServiceId
import org.wave.libwave.protocol.IdentityKey
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.state.IdentityKeyStore
import org.wave.libwave.protocol.state.IdentityKeyStore.IdentityChange
import org.thoughtcrime.securesms.database.WaveDatabase
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore

/**
 * An in-memory identity key store that is intended to be used temporarily while decrypting messages.
 */
class BufferedIdentityKeyStore(
  private val selfServiceId: ServiceId,
  private val selfIdentityKeyPair: IdentityKeyPair,
  private val selfRegistrationId: Int
) : IdentityKeyStore {

  private val store: MutableMap<WaveProtocolAddress, IdentityKey> = HashMap()

  /** All of the keys that have been created or updated during operation. */
  private val updatedKeys: MutableMap<WaveProtocolAddress, IdentityKey> = mutableMapOf()

  override fun getIdentityKeyPair(): IdentityKeyPair {
    return selfIdentityKeyPair
  }

  override fun getLocalRegistrationId(): Int {
    return selfRegistrationId
  }

  override fun saveIdentity(address: WaveProtocolAddress, identityKey: IdentityKey): IdentityChange {
    val existing: IdentityKey? = getIdentity(address)

    store[address] = identityKey

    return if (identityKey != existing) {
      updatedKeys[address] = identityKey
      IdentityChange.REPLACED_EXISTING
    } else {
      IdentityChange.NEW_OR_UNCHANGED
    }
  }

  override fun isTrustedIdentity(address: WaveProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean {
    if (address.name == selfServiceId.toString()) {
      return identityKey == selfIdentityKeyPair.publicKey
    }

    return when (direction) {
      IdentityKeyStore.Direction.RECEIVING -> true
      IdentityKeyStore.Direction.SENDING -> error("Should not happen during the intended usage pattern of this class")
      else -> error("Unknown direction: $direction")
    }
  }

  override fun getIdentity(address: WaveProtocolAddress): IdentityKey? {
    val cached = store[address]

    return if (cached != null) {
      cached
    } else {
      val fromDatabase = WaveDatabase.identities.getIdentityStoreRecord(address.name)
      if (fromDatabase != null) {
        store[address] = fromDatabase.identityKey
      }

      fromDatabase?.identityKey
    }
  }

  fun flushToDisk(persistentStore: WaveServiceAccountDataStore) {
    for ((address, identityKey) in updatedKeys) {
      persistentStore.saveIdentity(address, identityKey)
    }

    updatedKeys.clear()
  }
}

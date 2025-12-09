package org.thoughtcrime.securesms.messages.protocol

import org.wave.core.models.ServiceId
import org.wave.libwave.protocol.NoSessionException
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.state.SessionRecord
import org.thoughtcrime.securesms.database.WaveDatabase
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore
import org.whispersystems.waveservice.api.WaveServiceSessionStore
import kotlin.jvm.Throws

/**
 * An in-memory session store that is intended to be used temporarily while decrypting messages.
 */
class BufferedSessionStore(private val selfServiceId: ServiceId) : WaveServiceSessionStore {

  private val store: MutableMap<WaveProtocolAddress, SessionRecord> = HashMap()

  /** All of the sessions that have been created or updated during operation. */
  private val updatedSessions: MutableMap<WaveProtocolAddress, SessionRecord> = mutableMapOf()

  /** All of the sessions that have deleted during operation. */
  private val deletedSessions: MutableSet<WaveProtocolAddress> = mutableSetOf()

  override fun loadSession(address: WaveProtocolAddress): SessionRecord {
    val session: SessionRecord = store[address]
      ?: WaveDatabase.sessions.load(selfServiceId, address)
      ?: SessionRecord()

    store[address] = session

    return session
  }

  @Throws(NoSessionException::class)
  override fun loadExistingSessions(addresses: MutableList<WaveProtocolAddress>): List<SessionRecord> {
    val found: MutableList<SessionRecord?> = ArrayList(addresses.size)
    val needsDatabaseLookup: MutableList<Pair<Int, WaveProtocolAddress>> = mutableListOf()

    addresses.forEachIndexed { index, address ->
      val cached: SessionRecord? = store[address]

      if (cached != null) {
        found[index] = cached
      } else {
        needsDatabaseLookup += (index to address)
      }
    }

    if (needsDatabaseLookup.isNotEmpty()) {
      val databaseRecords: List<SessionRecord?> = WaveDatabase.sessions.load(selfServiceId, needsDatabaseLookup.map { (_, address) -> address })
      needsDatabaseLookup.forEachIndexed { databaseLookupIndex, (addressIndex, _) ->
        found[addressIndex] = databaseRecords[databaseLookupIndex]
      }
    }

    val cachedAndLoaded = found.filterNotNull()

    if (cachedAndLoaded.size != addresses.size) {
      throw NoSessionException("Failed to find one or more sessions.")
    }

    return cachedAndLoaded
  }

  override fun storeSession(address: WaveProtocolAddress, record: SessionRecord) {
    store[address] = record
    updatedSessions[address] = record
  }

  override fun containsSession(address: WaveProtocolAddress): Boolean {
    return if (store.containsKey(address)) {
      true
    } else {
      val fromDatabase: SessionRecord? = WaveDatabase.sessions.load(selfServiceId, address)

      if (fromDatabase != null) {
        store[address] = fromDatabase
        return fromDatabase.hasSenderChain()
      } else {
        false
      }
    }
  }

  override fun deleteSession(address: WaveProtocolAddress) {
    store.remove(address)
    deletedSessions += address
  }

  override fun getSubDeviceSessions(name: String): MutableList<Int> {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun deleteAllSessions(name: String) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun archiveSession(address: WaveProtocolAddress?) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): Map<WaveProtocolAddress, SessionRecord> {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: WaveServiceAccountDataStore) {
    for ((address, record) in updatedSessions) {
      persistentStore.storeSession(address, record)
    }

    for (address in deletedSessions) {
      persistentStore.deleteSession(address)
    }

    updatedSessions.clear()
    deletedSessions.clear()
  }
}

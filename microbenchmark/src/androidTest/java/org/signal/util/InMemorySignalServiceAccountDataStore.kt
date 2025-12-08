package org.wave.util

import org.wave.libwave.protocol.IdentityKey
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.ecc.ECPublicKey
import org.wave.libwave.protocol.groups.state.SenderKeyRecord
import org.wave.libwave.protocol.state.IdentityKeyStore
import org.wave.libwave.protocol.state.IdentityKeyStore.IdentityChange
import org.wave.libwave.protocol.state.KyberPreKeyRecord
import org.wave.libwave.protocol.state.PreKeyRecord
import org.wave.libwave.protocol.state.SessionRecord
import org.wave.libwave.protocol.state.SignedPreKeyRecord
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore
import org.whispersystems.waveservice.api.push.DistributionId
import java.util.UUID

/**
 * An in-memory datastore specifically designed for tests.
 */
class InMemoryWaveServiceAccountDataStore : WaveServiceAccountDataStore {

  private val identityKey: IdentityKeyPair = IdentityKeyPair.generate()
  private val identities: MutableMap<WaveProtocolAddress, IdentityKey> = mutableMapOf()
  private val oneTimeEcPreKeys: MutableMap<Int, PreKeyRecord> = mutableMapOf()
  private val signedPreKeys: MutableMap<Int, SignedPreKeyRecord> = mutableMapOf()
  private var sessions: MutableMap<WaveProtocolAddress, SessionRecord> = mutableMapOf()
  private val senderKeys: MutableMap<SenderKeyLocator, SenderKeyRecord> = mutableMapOf()
  private val kyberPreKeys: MutableMap<Int, KyberPreKeyRecord> = mutableMapOf()

  override fun getIdentityKeyPair(): IdentityKeyPair {
    return identityKey
  }

  override fun getLocalRegistrationId(): Int {
    return 1
  }

  override fun saveIdentity(address: WaveProtocolAddress, identityKey: IdentityKey): IdentityChange {
    val previous = identities.put(address, identityKey)
    return if (previous == null || previous == identityKey) {
      IdentityChange.NEW_OR_UNCHANGED
    } else {
      IdentityChange.REPLACED_EXISTING
    }
  }

  override fun isTrustedIdentity(address: WaveProtocolAddress?, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction?): Boolean {
    return true
  }

  override fun getIdentity(address: WaveProtocolAddress): IdentityKey? {
    return identities[address]
  }

  override fun loadPreKey(preKeyId: Int): PreKeyRecord {
    return oneTimeEcPreKeys[preKeyId]!!
  }

  override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
    oneTimeEcPreKeys[preKeyId] = record
  }

  override fun containsPreKey(preKeyId: Int): Boolean {
    return oneTimeEcPreKeys.containsKey(preKeyId)
  }

  override fun removePreKey(preKeyId: Int) {
    oneTimeEcPreKeys.remove(preKeyId)
  }

  override fun loadSession(address: WaveProtocolAddress): SessionRecord {
    return sessions.getOrPut(address) { SessionRecord() }
  }

  override fun loadExistingSessions(addresses: List<WaveProtocolAddress>): List<SessionRecord> {
    return addresses.map { sessions[it]!! }
  }

  override fun getSubDeviceSessions(name: String): List<Int> {
    return sessions
      .filter { it.key.name == name && it.key.deviceId != 1 && it.value.isValid() }
      .map { it.key.deviceId }
  }

  override fun storeSession(address: WaveProtocolAddress, record: SessionRecord) {
    sessions[address] = record
  }

  override fun containsSession(address: WaveProtocolAddress): Boolean {
    return sessions[address]?.isValid() ?: false
  }

  override fun deleteSession(address: WaveProtocolAddress) {
    sessions -= address
  }

  override fun deleteAllSessions(name: String) {
    sessions = sessions.filter { it.key.name == name }.toMutableMap()
  }

  override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
    return signedPreKeys[signedPreKeyId]!!
  }

  override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
    return signedPreKeys.values.toList()
  }

  override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
    signedPreKeys[signedPreKeyId] = record
  }

  override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
    return signedPreKeys.containsKey(signedPreKeyId)
  }

  override fun removeSignedPreKey(signedPreKeyId: Int) {
    signedPreKeys -= signedPreKeyId
  }

  override fun storeSenderKey(sender: WaveProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
    senderKeys[SenderKeyLocator(sender, distributionId)] = record
  }

  override fun loadSenderKey(sender: WaveProtocolAddress, distributionId: UUID): SenderKeyRecord? {
    return senderKeys[SenderKeyLocator(sender, distributionId)]
  }

  override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
    return kyberPreKeys[kyberPreKeyId]!!
  }

  override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
    return kyberPreKeys.values.toList()
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord?) {
    error("Not used")
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    return kyberPreKeys.containsKey(kyberPreKeyId)
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
    kyberPreKeys.remove(kyberPreKeyId)
  }

  override fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int) {
    error("Not used")
  }

  override fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long) {
    error("Not used")
  }

  override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) {
    error("Not used")
  }

  override fun removeKyberPreKey(kyberPreKeyId: Int) {
    error("Not used")
  }

  override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) {
    error("Not used")
  }

  override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) {
    error("Not used")
  }

  override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> {
    error("Not used")
  }

  override fun archiveSession(address: WaveProtocolAddress) {
    sessions[address]!!.archiveCurrentState()
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): MutableMap<WaveProtocolAddress, SessionRecord> {
    return sessions
      .filter { it.key.name in addressNames }
      .filter { it.value.isValid() }
      .toMutableMap()
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId): Set<WaveProtocolAddress> {
    error("Not used")
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId, addresses: Collection<WaveProtocolAddress>) {
    // Called, but not needed
  }

  override fun clearSenderKeySharedWith(addresses: Collection<WaveProtocolAddress>) {
    // Called, but not needed
  }

  override fun isMultiDevice(): Boolean {
    return false
  }

  private fun SessionRecord.isValid(): Boolean {
    return this.hasSenderChain()
  }

  private data class SenderKeyLocator(val address: WaveProtocolAddress, val distributionId: UUID)
}

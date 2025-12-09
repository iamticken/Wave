package org.thoughtcrime.securesms.messages.protocol

import org.wave.core.models.ServiceId
import org.wave.core.util.withinTransaction
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
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore
import org.whispersystems.waveservice.api.push.DistributionId
import java.util.UUID

/**
 * The wrapper around all of the Buffered protocol stores. Designed to perform operations in memory,
 * then [flushToDisk] at set intervals.
 */
class BufferedWaveServiceAccountDataStore(selfServiceId: ServiceId) : WaveServiceAccountDataStore {

  private val identityStore: BufferedIdentityKeyStore = if (selfServiceId == WaveStore.account.pni) {
    BufferedIdentityKeyStore(selfServiceId, WaveStore.account.pniIdentityKey, WaveStore.account.pniRegistrationId)
  } else {
    BufferedIdentityKeyStore(selfServiceId, WaveStore.account.aciIdentityKey, WaveStore.account.registrationId)
  }

  private val oneTimePreKeyStore: BufferedOneTimePreKeyStore = BufferedOneTimePreKeyStore(selfServiceId)
  private val signedPreKeyStore: BufferedSignedPreKeyStore = BufferedSignedPreKeyStore(selfServiceId)
  private val kyberPreKeyStore: BufferedKyberPreKeyStore = BufferedKyberPreKeyStore(selfServiceId)
  private val sessionStore: BufferedSessionStore = BufferedSessionStore(selfServiceId)
  private val senderKeyStore: BufferedSenderKeyStore = BufferedSenderKeyStore()

  override fun getIdentityKeyPair(): IdentityKeyPair {
    return identityStore.identityKeyPair
  }

  override fun getLocalRegistrationId(): Int {
    return identityStore.localRegistrationId
  }

  override fun saveIdentity(address: WaveProtocolAddress, identityKey: IdentityKey): IdentityChange {
    return identityStore.saveIdentity(address, identityKey)
  }

  override fun isTrustedIdentity(address: WaveProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean {
    return identityStore.isTrustedIdentity(address, identityKey, direction)
  }

  override fun getIdentity(address: WaveProtocolAddress): IdentityKey? {
    return identityStore.getIdentity(address)
  }

  override fun loadPreKey(preKeyId: Int): PreKeyRecord {
    return oneTimePreKeyStore.loadPreKey(preKeyId)
  }

  override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
    return oneTimePreKeyStore.storePreKey(preKeyId, record)
  }

  override fun containsPreKey(preKeyId: Int): Boolean {
    return oneTimePreKeyStore.containsPreKey(preKeyId)
  }

  override fun removePreKey(preKeyId: Int) {
    oneTimePreKeyStore.removePreKey(preKeyId)
  }

  override fun loadSession(address: WaveProtocolAddress): SessionRecord {
    return sessionStore.loadSession(address)
  }

  override fun loadExistingSessions(addresses: MutableList<WaveProtocolAddress>): List<SessionRecord> {
    return sessionStore.loadExistingSessions(addresses)
  }

  override fun getSubDeviceSessions(name: String): MutableList<Int> {
    return sessionStore.getSubDeviceSessions(name)
  }

  override fun storeSession(address: WaveProtocolAddress, record: SessionRecord) {
    sessionStore.storeSession(address, record)
  }

  override fun containsSession(address: WaveProtocolAddress): Boolean {
    return sessionStore.containsSession(address)
  }

  override fun deleteSession(address: WaveProtocolAddress) {
    return sessionStore.deleteSession(address)
  }

  override fun deleteAllSessions(name: String) {
    sessionStore.deleteAllSessions(name)
  }

  override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
    return signedPreKeyStore.loadSignedPreKey(signedPreKeyId)
  }

  override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
    return signedPreKeyStore.loadSignedPreKeys()
  }

  override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
    signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record)
  }

  override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
    return signedPreKeyStore.containsSignedPreKey(signedPreKeyId)
  }

  override fun removeSignedPreKey(signedPreKeyId: Int) {
    signedPreKeyStore.removeSignedPreKey(signedPreKeyId)
  }

  override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
    return kyberPreKeyStore.loadKyberPreKey(kyberPreKeyId)
  }

  override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
    return kyberPreKeyStore.loadKyberPreKeys()
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
    kyberPreKeyStore.storeKyberPreKey(kyberPreKeyId, record)
  }

  override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) {
    kyberPreKeyStore.storeKyberPreKey(kyberPreKeyId, kyberPreKeyRecord)
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    return kyberPreKeyStore.containsKyberPreKey(kyberPreKeyId)
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, publicKey: ECPublicKey) {
    return kyberPreKeyStore.markKyberPreKeyUsed(kyberPreKeyId, signedPreKeyId, publicKey)
  }

  override fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun removeKyberPreKey(kyberPreKeyId: Int) {
    kyberPreKeyStore.removeKyberPreKey(kyberPreKeyId)
  }

  override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) {
    kyberPreKeyStore.markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime)
  }

  override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) {
    kyberPreKeyStore.deleteAllStaleOneTimeKyberPreKeys(threshold, minCount)
  }

  override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> {
    return kyberPreKeyStore.loadLastResortKyberPreKeys()
  }

  override fun storeSenderKey(sender: WaveProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
    senderKeyStore.storeSenderKey(sender, distributionId, record)
  }

  override fun loadSenderKey(sender: WaveProtocolAddress, distributionId: UUID): SenderKeyRecord? {
    return senderKeyStore.loadSenderKey(sender, distributionId)
  }

  override fun archiveSession(address: WaveProtocolAddress?) {
    sessionStore.archiveSession(address)
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): Map<WaveProtocolAddress, SessionRecord> {
    return sessionStore.getAllAddressesWithActiveSessions(addressNames)
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<WaveProtocolAddress> {
    return senderKeyStore.getSenderKeySharedWith(distributionId)
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId, addresses: MutableCollection<WaveProtocolAddress>) {
    senderKeyStore.markSenderKeySharedWith(distributionId, addresses)
  }

  override fun clearSenderKeySharedWith(addresses: MutableCollection<WaveProtocolAddress>) {
    senderKeyStore.clearSenderKeySharedWith(addresses)
  }

  override fun isMultiDevice(): Boolean {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: WaveServiceAccountDataStore) {
    WaveDatabase.writableDatabase.withinTransaction {
      identityStore.flushToDisk(persistentStore)
      oneTimePreKeyStore.flushToDisk(persistentStore)
      kyberPreKeyStore.flushToDisk(persistentStore)
      signedPreKeyStore.flushToDisk(persistentStore)
      sessionStore.flushToDisk(persistentStore)
      senderKeyStore.flushToDisk(persistentStore)
    }
  }
}

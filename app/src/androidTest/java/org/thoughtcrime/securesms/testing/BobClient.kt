package org.thoughtcrime.securesms.testing

import org.wave.core.models.ServiceId
import org.wave.core.util.readToSingleInt
import org.wave.core.util.select
import org.wave.libwave.protocol.IdentityKey
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.SessionBuilder
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.ecc.ECKeyPair
import org.wave.libwave.protocol.ecc.ECPublicKey
import org.wave.libwave.protocol.groups.state.SenderKeyRecord
import org.wave.libwave.protocol.state.IdentityKeyStore
import org.wave.libwave.protocol.state.IdentityKeyStore.IdentityChange
import org.wave.libwave.protocol.state.KyberPreKeyRecord
import org.wave.libwave.protocol.state.PreKeyBundle
import org.wave.libwave.protocol.state.PreKeyRecord
import org.wave.libwave.protocol.state.SessionRecord
import org.wave.libwave.protocol.state.SignedPreKeyRecord
import org.wave.libwave.protocol.util.KeyHelper
import org.wave.libwave.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.KyberPreKeyTable
import org.thoughtcrime.securesms.database.OneTimePreKeyTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.SignedPreKeyTable
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.testing.FakeClientHelpers.toEnvelope
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore
import org.whispersystems.waveservice.api.WaveSessionLock
import org.whispersystems.waveservice.api.crypto.SealedSenderAccess
import org.whispersystems.waveservice.api.crypto.WaveServiceCipher
import org.whispersystems.waveservice.api.crypto.WaveSessionBuilder
import org.whispersystems.waveservice.api.push.DistributionId
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import org.whispersystems.waveservice.internal.push.Envelope
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/**
 * Welcome to Bob's Client.
 *
 * Bob is a "fake" client that can start a session with the Android instrumentation test user (Alice).
 *
 * Bob can create a new session using a prekey bundle created from Alice's prekeys, send a message, decrypt
 * a return message from Alice, and that'll start a standard Wave session with normal keys/ratcheting.
 */
class BobClient(val serviceId: ServiceId, val e164: String, val identityKeyPair: IdentityKeyPair, val trustRoot: ECKeyPair, val profileKey: ProfileKey) {

  private val serviceAddress = WaveServiceAddress(serviceId, e164)
  private val registrationId = KeyHelper.generateRegistrationId(false)
  private val aciStore = BobWaveServiceAccountDataStore(registrationId, identityKeyPair)
  private val senderCertificate = FakeClientHelpers.createCertificateFor(trustRoot, serviceId.rawUuid, e164, 1, identityKeyPair.publicKey.publicKey, 31337)
  private val sessionLock = object : WaveSessionLock {
    private val lock = ReentrantLock()

    override fun acquire(): WaveSessionLock.Lock {
      lock.lock()
      return WaveSessionLock.Lock { lock.unlock() }
    }
  }

  /** Inspired by WaveServiceMessageSender#getEncryptedMessage */
  fun encrypt(now: Long): Envelope {
    val envelopeContent = FakeClientHelpers.encryptedTextMessage(now)

    val cipher = WaveServiceCipher(serviceAddress, 1, aciStore, sessionLock, null)

    if (!aciStore.containsSession(getAliceProtocolAddress())) {
      val sessionBuilder = WaveSessionBuilder(sessionLock, SessionBuilder(aciStore, getAliceProtocolAddress()))
      sessionBuilder.process(getAlicePreKeyBundle())
    }

    return cipher.encrypt(getAliceProtocolAddress(), getAliceUnidentifiedAccess(), envelopeContent)
      .toEnvelope(envelopeContent.content.get().dataMessage!!.timestamp!!, getAliceServiceId())
  }

  fun decrypt(envelope: Envelope, serverDeliveredTimestamp: Long) {
    val cipher = WaveServiceCipher(serviceAddress, 1, aciStore, sessionLock, SealedSenderAccessUtil.getCertificateValidator())
    cipher.decrypt(envelope, serverDeliveredTimestamp)
  }

  private fun getAliceServiceId(): ServiceId {
    return WaveStore.account.requireAci()
  }

  private fun getAlicePreKeyBundle(): PreKeyBundle {
    val selfPreKeyId = WaveDatabase.rawDatabase
      .select(OneTimePreKeyTable.KEY_ID)
      .from(OneTimePreKeyTable.TABLE_NAME)
      .where("${OneTimePreKeyTable.ACCOUNT_ID} = ?", getAliceServiceId().toString())
      .run()
      .readToSingleInt(-1)

    val selfPreKeyRecord = WaveDatabase.oneTimePreKeys.get(getAliceServiceId(), selfPreKeyId)!!

    val selfSignedPreKeyId = WaveDatabase.rawDatabase
      .select(SignedPreKeyTable.KEY_ID)
      .from(SignedPreKeyTable.TABLE_NAME)
      .where("${SignedPreKeyTable.ACCOUNT_ID} = ?", getAliceServiceId().toString())
      .run()
      .readToSingleInt(-1)

    val selfSignedPreKeyRecord = WaveDatabase.signedPreKeys.get(getAliceServiceId(), selfSignedPreKeyId)!!

    val selfSignedKyberPreKeyId = WaveDatabase.rawDatabase
      .select(KyberPreKeyTable.KEY_ID)
      .from(KyberPreKeyTable.TABLE_NAME)
      .where("${KyberPreKeyTable.ACCOUNT_ID} = ?", getAliceServiceId().toString())
      .run()
      .readToSingleInt(-1)

    val selfSignedKyberPreKeyRecord = WaveDatabase.kyberPreKeys.get(getAliceServiceId(), selfSignedKyberPreKeyId)!!.record

    return PreKeyBundle(
      WaveStore.account.registrationId,
      1,
      selfPreKeyId,
      selfPreKeyRecord.keyPair.publicKey,
      selfSignedPreKeyId,
      selfSignedPreKeyRecord.keyPair.publicKey,
      selfSignedPreKeyRecord.signature,
      getAlicePublicKey(),
      selfSignedKyberPreKeyId,
      selfSignedKyberPreKeyRecord.keyPair.publicKey,
      selfSignedKyberPreKeyRecord.signature
    )
  }

  private fun getAliceProtocolAddress(): WaveProtocolAddress {
    return WaveProtocolAddress(WaveStore.account.requireAci().toString(), 1)
  }

  private fun getAlicePublicKey(): IdentityKey {
    return WaveStore.account.aciIdentityKey.publicKey
  }

  private fun getAliceProfileKey(): ProfileKey {
    return ProfileKeyUtil.getSelfProfileKey()
  }

  private fun getAliceUnidentifiedAccess(): SealedSenderAccess? {
    return FakeClientHelpers.getSealedSenderAccess(getAliceProfileKey(), senderCertificate)
  }

  private class BobWaveServiceAccountDataStore(private val registrationId: Int, private val identityKeyPair: IdentityKeyPair) : WaveServiceAccountDataStore {
    private var aliceSessionRecord: SessionRecord? = null

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = registrationId
    override fun isTrustedIdentity(address: WaveProtocolAddress?, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction?): Boolean = true
    override fun loadSession(address: WaveProtocolAddress?): SessionRecord = aliceSessionRecord ?: SessionRecord()
    override fun saveIdentity(address: WaveProtocolAddress?, identityKey: IdentityKey?): IdentityKeyStore.IdentityChange = IdentityChange.NEW_OR_UNCHANGED
    override fun storeSession(address: WaveProtocolAddress?, record: SessionRecord?) {
      aliceSessionRecord = record
    }
    override fun getSubDeviceSessions(name: String?): List<Int> = emptyList()
    override fun containsSession(address: WaveProtocolAddress?): Boolean = aliceSessionRecord != null
    override fun getIdentity(address: WaveProtocolAddress?): IdentityKey = WaveStore.account.aciIdentityKey.publicKey
    override fun loadPreKey(preKeyId: Int): PreKeyRecord = throw UnsupportedOperationException()
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsPreKey(preKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun removePreKey(preKeyId: Int) = throw UnsupportedOperationException()
    override fun loadExistingSessions(addresses: MutableList<WaveProtocolAddress>?): MutableList<SessionRecord> = throw UnsupportedOperationException()
    override fun deleteSession(address: WaveProtocolAddress?) = throw UnsupportedOperationException()
    override fun deleteAllSessions(name: String?) = throw UnsupportedOperationException()
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = throw UnsupportedOperationException()
    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = throw UnsupportedOperationException()
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun removeSignedPreKey(signedPreKeyId: Int) = throw UnsupportedOperationException()
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord = throw UnsupportedOperationException()
    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = throw UnsupportedOperationException()
    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) = throw UnsupportedOperationException()
    override fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int) = throw UnsupportedOperationException()
    override fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long) = throw UnsupportedOperationException()
    override fun storeSenderKey(sender: WaveProtocolAddress?, distributionId: UUID?, record: SenderKeyRecord?) = throw UnsupportedOperationException()
    override fun loadSenderKey(sender: WaveProtocolAddress?, distributionId: UUID?): SenderKeyRecord = throw UnsupportedOperationException()
    override fun archiveSession(address: WaveProtocolAddress?) = throw UnsupportedOperationException()
    override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>?): MutableMap<WaveProtocolAddress, SessionRecord> = throw UnsupportedOperationException()
    override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<WaveProtocolAddress> = throw UnsupportedOperationException()
    override fun markSenderKeySharedWith(distributionId: DistributionId?, addresses: MutableCollection<WaveProtocolAddress>?) = throw UnsupportedOperationException()
    override fun clearSenderKeySharedWith(addresses: MutableCollection<WaveProtocolAddress>?) = throw UnsupportedOperationException()
    override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) = throw UnsupportedOperationException()
    override fun removeKyberPreKey(kyberPreKeyId: Int) = throw UnsupportedOperationException()
    override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) = throw UnsupportedOperationException()
    override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) = throw UnsupportedOperationException()
    override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> = throw UnsupportedOperationException()
    override fun isMultiDevice(): Boolean = throw UnsupportedOperationException()
  }
}

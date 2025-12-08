package org.wave.util

import okio.ByteString.Companion.toByteString
import org.wave.core.models.ServiceId
import org.wave.core.util.Base64
import org.wave.core.util.toByteArray
import org.wave.libwave.metadata.certificate.CertificateValidator
import org.wave.libwave.metadata.certificate.SenderCertificate
import org.wave.libwave.metadata.certificate.ServerCertificate
import org.wave.libwave.protocol.SessionBuilder
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.ecc.ECKeyPair
import org.wave.libwave.protocol.ecc.ECPublicKey
import org.wave.libwave.protocol.groups.GroupSessionBuilder
import org.wave.libwave.protocol.kem.KEMKeyPair
import org.wave.libwave.protocol.kem.KEMKeyType
import org.wave.libwave.protocol.message.SenderKeyDistributionMessage
import org.wave.libwave.protocol.state.PreKeyBundle
import org.wave.libwave.protocol.state.PreKeyRecord
import org.wave.libwave.protocol.state.SignedPreKeyRecord
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore
import org.whispersystems.waveservice.api.WaveSessionLock
import org.whispersystems.waveservice.api.crypto.ContentHint
import org.whispersystems.waveservice.api.crypto.EnvelopeContent
import org.whispersystems.waveservice.api.crypto.SealedSenderAccess
import org.whispersystems.waveservice.api.crypto.WaveGroupSessionBuilder
import org.whispersystems.waveservice.api.crypto.WaveServiceCipher
import org.whispersystems.waveservice.api.crypto.UnidentifiedAccess
import org.whispersystems.waveservice.api.push.DistributionId
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import org.whispersystems.waveservice.internal.push.Content
import org.whispersystems.waveservice.internal.push.DataMessage
import org.whispersystems.waveservice.internal.push.Envelope
import org.whispersystems.waveservice.internal.push.OutgoingPushMessage
import org.whispersystems.waveservice.internal.util.Util
import java.util.Optional
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

/**
 * An in-memory wave client that can encrypt and decrypt messages.
 *
 * Has a single prekey bundle that can be used to initialize a session with another client.
 */
class WaveClient {
  companion object {
    private val trustRoot: ECKeyPair = ECKeyPair.generate()
  }

  private val lock = TestSessionLock()

  private val aci: ServiceId.ACI = ServiceId.ACI.from(UUID.randomUUID())

  private val store: WaveServiceAccountDataStore = InMemoryWaveServiceAccountDataStore()

  private var prekeyIndex = 0

  private val unidentifiedAccessKey: ByteArray = Util.getSecretBytes(32)

  private val senderCertificate: SenderCertificate = createCertificateFor(
    trustRoot = trustRoot,
    uuid = aci.rawUuid,
    e164 = "+${Random.nextLong(1111111111L, 9999999999L)}",
    deviceId = 1,
    identityKey = store.identityKeyPair.publicKey.publicKey,
    expires = Long.MAX_VALUE
  )

  private val cipher = WaveServiceCipher(WaveServiceAddress(aci), 1, store, lock, CertificateValidator(trustRoot.publicKey))

  /**
   * Sets up sessions using the [to] client's [preKeyBundles]. Note that you can only initialize a client up to 1,000 times because that's how many prekeys we have.
   */
  fun initializeSession(to: WaveClient) {
    val address = WaveProtocolAddress(to.aci.toString(), 1)
    SessionBuilder(store, address).process(to.createPreKeyBundle())
  }

  fun initializedGroupSession(distributionId: DistributionId): SenderKeyDistributionMessage {
    val self = WaveProtocolAddress(aci.toString(), 1)
    return WaveGroupSessionBuilder(lock, GroupSessionBuilder(store)).create(self, distributionId.asUuid())
  }

  fun encryptUnsealedSender(to: WaveClient): Envelope {
    val sentTimestamp = System.currentTimeMillis()

    val content = Content(
      dataMessage = DataMessage(
        body = "Test Message",
        timestamp = sentTimestamp
      )
    )

    val outgoingPushMessage: OutgoingPushMessage = cipher.encrypt(
      WaveProtocolAddress(to.aci.toString(), 1),
      SealedSenderAccess.NONE,
      EnvelopeContent.encrypted(content, ContentHint.RESENDABLE, Optional.empty())
    )

    val encryptedContent: ByteArray = Base64.decode(outgoingPushMessage.content)
    val serviceGuid = UUID.randomUUID()

    return Envelope(
      sourceServiceId = aci.toString(),
      sourceDevice = 1,
      destinationServiceId = to.aci.toString(),
      timestamp = sentTimestamp,
      serverTimestamp = sentTimestamp,
      serverGuid = serviceGuid.toString(),
      type = Envelope.Type.fromValue(outgoingPushMessage.type),
      urgent = true,
      content = encryptedContent.toByteString(),
      sourceServiceIdBinary = aci.toByteString(),
      destinationServiceIdBinary = to.aci.toByteString(),
      serverGuidBinary = serviceGuid.toByteArray().toByteString()
    )
  }

  fun encryptSealedSender(to: WaveClient): Envelope {
    val sentTimestamp = System.currentTimeMillis()

    val content = Content(
      dataMessage = DataMessage(
        body = "Test Message",
        timestamp = sentTimestamp
      )
    )

    val outgoingPushMessage: OutgoingPushMessage = cipher.encrypt(
      WaveProtocolAddress(to.aci.toString(), 1),
      SealedSenderAccess.forIndividual(UnidentifiedAccess(to.unidentifiedAccessKey, senderCertificate.serialized, false)),
      EnvelopeContent.encrypted(content, ContentHint.RESENDABLE, Optional.empty())
    )

    val encryptedContent: ByteArray = Base64.decode(outgoingPushMessage.content)
    val serverGuid = UUID.randomUUID()

    return Envelope(
      sourceServiceId = aci.toString(),
      sourceDevice = 1,
      destinationServiceId = to.aci.toString(),
      timestamp = sentTimestamp,
      serverTimestamp = sentTimestamp,
      serverGuid = serverGuid.toString(),
      type = Envelope.Type.fromValue(outgoingPushMessage.type),
      urgent = true,
      content = encryptedContent.toByteString(),
      sourceServiceIdBinary = aci.toByteString(),
      destinationServiceIdBinary = to.aci.toByteString(),
      serverGuidBinary = serverGuid.toByteArray().toByteString()
    )
  }

  fun multiEncryptSealedSender(distributionId: DistributionId, others: List<WaveClient>, groupId: Optional<ByteArray>): ByteArray {
    val sentTimestamp = System.currentTimeMillis()

    val content = Content(
      dataMessage = DataMessage(
        body = "Test Message",
        timestamp = sentTimestamp
      )
    )
    val destinations = others.map { bob ->
      WaveProtocolAddress(bob.aci.toString(), 1)
    }

    return cipher.encryptForGroup(distributionId, destinations, null, senderCertificate, content.encode(), ContentHint.DEFAULT, groupId)
  }

  fun decryptMessage(envelope: Envelope) {
    cipher.decrypt(envelope, System.currentTimeMillis())
  }

  private fun createPreKeyBundle(): PreKeyBundle {
    val prekeyId = prekeyIndex++
    val preKeyRecord = PreKeyRecord(prekeyId, ECKeyPair.generate())
    val signedPreKeyPair = ECKeyPair.generate()
    val signedPreKeySignature = store.identityKeyPair.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
    val kyerPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)

    store.storePreKey(prekeyId, preKeyRecord)
    store.storeSignedPreKey(prekeyId, SignedPreKeyRecord(prekeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature))

    return PreKeyBundle(
      prekeyId, prekeyId, prekeyId, preKeyRecord.keyPair.publicKey, prekeyId, signedPreKeyPair.publicKey, signedPreKeySignature, store.identityKeyPair.publicKey,
      PreKeyBundle.NULL_PRE_KEY_ID, kyerPair.publicKey, kyerPair.secretKey.serialize()
    )
  }
}

private fun createCertificateFor(trustRoot: ECKeyPair, uuid: UUID, e164: String, deviceId: Int, identityKey: ECPublicKey, expires: Long): SenderCertificate {
  val serverKey: ECKeyPair = ECKeyPair.generate()
  val serverCertificate = ServerCertificate(trustRoot.privateKey, 1, serverKey.publicKey)
  return serverCertificate.issue(serverKey.privateKey, uuid.toString(), Optional.of(e164), deviceId, identityKey, expires)
}

private class TestSessionLock : WaveSessionLock {
  val lock = ReentrantLock()

  override fun acquire(): WaveSessionLock.Lock {
    lock.lock()
    return WaveSessionLock.Lock { lock.unlock() }
  }
}

package org.thoughtcrime.securesms.testing

import org.wave.core.models.ServiceId
import org.wave.core.util.logging.Log
import org.wave.libwave.protocol.ecc.ECKeyPair
import org.wave.libwave.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.messages.protocol.BufferedProtocolStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.FakeClientHelpers.toEnvelope
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import org.whispersystems.waveservice.internal.push.Envelope

/**
 * Welcome to Alice's Client.
 *
 * Alice represent the Android instrumentation test user. Unlike [BobClient] much less is needed here
 * as it can make use of the standard Wave Android App infrastructure.
 */
class AliceClient(val serviceId: ServiceId, val e164: String, val trustRoot: ECKeyPair) {

  companion object {
    val TAG = Log.tag(AliceClient::class.java)
  }

  private val aliceSenderCertificate = FakeClientHelpers.createCertificateFor(
    trustRoot = trustRoot,
    uuid = serviceId.rawUuid,
    e164 = e164,
    deviceId = 1,
    identityKey = WaveStore.account.aciIdentityKey.publicKey.publicKey,
    expires = 31337
  )

  fun process(envelope: Envelope, serverDeliveredTimestamp: Long) {
    val start = System.currentTimeMillis()
    val bufferedStore = BufferedProtocolStore.create()
    AppDependencies.incomingMessageObserver
      .processEnvelope(bufferedStore, envelope, serverDeliveredTimestamp)
      ?.mapNotNull { it.run() }
      ?.forEach { it.enqueue() }

    bufferedStore.flushToDisk()
    val end = System.currentTimeMillis()
    Log.d(TAG, "${end - start}")
  }

  fun encrypt(now: Long, destination: Recipient): Envelope {
    return AppDependencies.waveServiceMessageSender.getEncryptedMessage(
      WaveServiceAddress(destination.requireServiceId(), destination.requireE164()),
      FakeClientHelpers.getSealedSenderAccess(ProfileKey(destination.profileKey), aliceSenderCertificate),
      1,
      FakeClientHelpers.encryptedTextMessage(now),
      false
    ).toEnvelope(now, destination.requireServiceId())
  }
}

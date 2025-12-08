package org.thoughtcrime.securesms.testing

import org.wave.core.models.ServiceId
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.ecc.ECKeyPair
import org.wave.libwave.protocol.state.PreKeyRecord
import org.wave.libwave.protocol.util.KeyHelper
import org.wave.libwave.protocol.util.Medium
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.whispersystems.waveservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.waveservice.api.push.SignedPreKeyEntity
import org.whispersystems.waveservice.internal.push.AuthCredentials
import org.whispersystems.waveservice.internal.push.DeviceInfoList
import org.whispersystems.waveservice.internal.push.PreKeyEntity
import org.whispersystems.waveservice.internal.push.PreKeyResponse
import org.whispersystems.waveservice.internal.push.PreKeyResponseItem
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import org.whispersystems.waveservice.internal.push.RegistrationSessionMetadataJson
import org.whispersystems.waveservice.internal.push.SenderCertificate
import org.whispersystems.waveservice.internal.push.VerifyAccountResponse
import org.whispersystems.waveservice.internal.push.WhoAmIResponse
import java.security.SecureRandom

/**
 * Warehouse of reusable test data and mock configurations.
 */
object MockProvider {

  val senderCertificate = SenderCertificate().apply { certificate = ByteArray(0) }

  val lockedFailure = PushServiceSocket.RegistrationLockFailure().apply {
    svr1Credentials = AuthCredentials.create("username", "password")
    svr2Credentials = AuthCredentials.create("username", "password")
  }

  val primaryOnlyDeviceList = DeviceInfoList().apply {
    devices = listOf(
      DeviceInfo().apply {
        id = 1
      }
    )
  }

  val sessionMetadataJson = RegistrationSessionMetadataJson(
    id = "asdfasdfasdfasdf",
    nextCall = null,
    nextSms = null,
    nextVerificationAttempt = null,
    allowedToRequestCode = true,
    requestedInformation = emptyList(),
    verified = true
  )

  fun createVerifyAccountResponse(aci: ServiceId, newPni: ServiceId): VerifyAccountResponse {
    return VerifyAccountResponse().apply {
      uuid = aci.toString()
      pni = newPni.toString()
      storageCapable = false
    }
  }

  fun createWhoAmIResponse(aci: ServiceId, pni: ServiceId, e164: String): WhoAmIResponse {
    return WhoAmIResponse(
      aci = aci.toString(),
      pni = pni.toString(),
      number = e164
    )
  }

  fun createPreKeyResponse(identity: IdentityKeyPair = WaveStore.account.aciIdentityKey, deviceId: Int): PreKeyResponse {
    val signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), identity.privateKey)
    val oneTimePreKey = PreKeyRecord(SecureRandom().nextInt(Medium.MAX_VALUE), ECKeyPair.generate())

    val device = PreKeyResponseItem().apply {
      this.deviceId = deviceId
      registrationId = KeyHelper.generateRegistrationId(false)
      signedPreKey = SignedPreKeyEntity(signedPreKeyRecord.id, signedPreKeyRecord.keyPair.publicKey, signedPreKeyRecord.signature)
      preKey = PreKeyEntity(oneTimePreKey.id, oneTimePreKey.keyPair.publicKey)
    }

    return PreKeyResponse().apply {
      identityKey = identity.publicKey
      devices = listOf(device)
    }
  }
}

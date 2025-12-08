/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.toByteString
import org.wave.core.models.MasterKey
import org.wave.core.models.ServiceId
import org.wave.core.util.logging.Log
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.state.KyberPreKeyRecord
import org.wave.libwave.protocol.state.WaveProtocolStore
import org.wave.libwave.protocol.state.SignedPreKeyRecord
import org.wave.libwave.protocol.util.KeyHelper
import org.wave.libwave.protocol.util.Medium
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingChangeNumberMetadata
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.keyvalue.CertificateType
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.net.WaveNetwork
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.WaveServiceAccountManager
import org.whispersystems.waveservice.api.WaveServiceMessageSender
import org.whispersystems.waveservice.api.SvrNoDataException
import org.whispersystems.waveservice.api.account.ChangePhoneNumberRequest
import org.whispersystems.waveservice.api.account.PreKeyUpload
import org.whispersystems.waveservice.api.push.ServiceIdType
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import org.whispersystems.waveservice.api.push.SignedPreKeyEntity
import org.whispersystems.waveservice.internal.push.KyberPreKeyEntity
import org.whispersystems.waveservice.internal.push.MismatchedDevices
import org.whispersystems.waveservice.internal.push.OutgoingPushMessage
import org.whispersystems.waveservice.internal.push.SyncMessage
import org.whispersystems.waveservice.internal.push.VerifyAccountResponse
import org.whispersystems.waveservice.internal.push.WhoAmIResponse
import java.io.IOException
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Repository to perform data operations during change number.
 *
 * @see [org.thoughtcrime.securesms.registration.data.RegistrationRepository]
 */
class ChangeNumberRepository(
  private val accountManager: WaveServiceAccountManager = AppDependencies.waveServiceAccountManager,
  private val messageSender: WaveServiceMessageSender = AppDependencies.waveServiceMessageSender
) {

  companion object {
    private val TAG = Log.tag(ChangeNumberRepository::class.java)
  }

  fun whoAmI(): WhoAmIResponse {
    return accountManager.whoAmI
  }

  suspend fun ensureDecryptionsDrained(timeout: Duration = 15.seconds) =
    withTimeoutOrNull(timeout) {
      suspendCancellableCoroutine {
        val drainedListener = object : Runnable {
          override fun run() {
            AppDependencies
              .incomingMessageObserver
              .removeDecryptionDrainedListener(this)
            Log.d(TAG, "Decryptions drained.")
            it.resume(true)
          }
        }

        it.invokeOnCancellation { cancellationCause ->
          AppDependencies
            .incomingMessageObserver
            .removeDecryptionDrainedListener(drainedListener)
          Log.d(TAG, "Decryptions draining canceled.", cancellationCause)
        }

        AppDependencies
          .incomingMessageObserver
          .addDecryptionDrainedListener(drainedListener)
        Log.d(TAG, "Waiting for decryption drain.")
      }
    }

  @WorkerThread
  fun changeLocalNumber(e164: String, pni: ServiceId.PNI) {
    WaveDatabase.recipients.updateSelfE164(e164, pni)
    AppDependencies.recipientCache.clear()

    if (e164 != WaveStore.account.requireE164()) {
      WaveDatabase.recipients.rotateStorageId(Recipient.self().fresh().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    WaveStore.account.setE164(e164)
    WaveStore.account.setPni(pni)
    AppDependencies.resetProtocolStores()

    AppDependencies.groupsV2Authorization.clear()

    val metadata: PendingChangeNumberMetadata? = WaveStore.misc.pendingChangeNumberMetadata
    if (metadata == null) {
      Log.w(TAG, "No change number metadata, this shouldn't happen")
      throw AssertionError("No change number metadata")
    }

    val pniIdentityKeyPair = IdentityKeyPair(metadata.pniIdentityKeyPair.toByteArray())
    val pniRegistrationId = metadata.pniRegistrationId
    val pniSignedPreyKeyId = metadata.pniSignedPreKeyId
    val pniLastResortKyberPreKeyId = metadata.pniLastResortKyberPreKeyId

    val pniProtocolStore = AppDependencies.protocolStore.pni()
    val pniMetadataStore = WaveStore.account.pniPreKeys

    WaveStore.account.pniRegistrationId = pniRegistrationId
    WaveStore.account.setPniIdentityKeyAfterChangeNumber(pniIdentityKeyPair)

    val signedPreKey = pniProtocolStore.loadSignedPreKey(pniSignedPreyKeyId)
    val oneTimeEcPreKeys = PreKeyUtil.generateAndStoreOneTimeEcPreKeys(pniProtocolStore, pniMetadataStore)
    val lastResortKyberPreKey = pniProtocolStore.loadLastResortKyberPreKeys().firstOrNull { it.id == pniLastResortKyberPreKeyId }
    val oneTimeKyberPreKeys = PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(pniProtocolStore, pniMetadataStore)

    if (lastResortKyberPreKey == null) {
      Log.w(TAG, "Last-resort kyber prekey is missing!")
    }

    pniMetadataStore.activeSignedPreKeyId = signedPreKey.id
    Log.i(TAG, "Submitting prekeys with PNI identity key: ${pniIdentityKeyPair.publicKey.fingerprint}")

    retryChangeLocalNumberNetworkOperation {
      WaveNetwork.keys.setPreKeys(
        PreKeyUpload(
          serviceIdType = ServiceIdType.PNI,
          signedPreKey = signedPreKey,
          oneTimeEcPreKeys = oneTimeEcPreKeys,
          lastResortKyberPreKey = lastResortKyberPreKey,
          oneTimeKyberPreKeys = oneTimeKyberPreKeys
        )
      )
    }.successOrThrow()

    pniMetadataStore.isSignedPreKeyRegistered = true
    pniMetadataStore.lastResortKyberPreKeyId = pniLastResortKyberPreKeyId

    pniProtocolStore.identities().saveIdentityWithoutSideEffects(
      Recipient.self().id,
      pni,
      pniProtocolStore.identityKeyPair.publicKey,
      IdentityTable.VerifiedStatus.VERIFIED,
      true,
      System.currentTimeMillis(),
      true
    )

    WaveStore.misc.hasPniInitializedDevices = true
    AppDependencies.groupsV2Authorization.clear()

    Recipient.self().fresh()
    StorageSyncHelper.scheduleSyncForDataChange()

    AppDependencies.resetNetwork()
    AppDependencies.startNetwork()

    AppDependencies.jobManager.add(RefreshAttributesJob())

    rotateCertificates()

    WaveStore.misc.unlockChangeNumber()
  }

  @WorkerThread
  private fun rotateCertificates() {
    val certificateTypes = WaveStore.phoneNumberPrivacy.allCertificateTypes

    Log.i(TAG, "Rotating these certificates $certificateTypes")

    for (certificateType in certificateTypes) {
      val certificate: ByteArray? = when (certificateType) {
        CertificateType.ACI_AND_E164 -> retryChangeLocalNumberNetworkOperation { WaveNetwork.certificate.getSenderCertificate() }.successOrThrow()
        CertificateType.ACI_ONLY -> retryChangeLocalNumberNetworkOperation { WaveNetwork.certificate.getSenderCertificateForPhoneNumberPrivacy() }.successOrThrow()
        else -> throw AssertionError()
      }

      Log.i(TAG, "Successfully got $certificateType certificate")

      WaveStore.certificate.setUnidentifiedAccessCertificate(certificateType, certificate)
    }
  }

  private fun <T> retryChangeLocalNumberNetworkOperation(operation: () -> NetworkResult<T>): NetworkResult<T> {
    var tries = 0
    var result = operation()
    while (tries < 5) {
      when (result) {
        is NetworkResult.Success,
        is NetworkResult.ApplicationError -> return result
        is NetworkResult.StatusCodeError,
        is NetworkResult.NetworkError -> Log.w(TAG, "Network related error attempting change number operation, try: $tries", result.getCause())
      }

      tries++
      BackoffUtil.exponentialBackoff(tries, 10.seconds.inWholeMilliseconds)
      result = operation()
    }

    return result
  }

  suspend fun changeNumberWithRecoveryPassword(recoveryPassword: String, newE164: String): ChangeNumberResult {
    return changeNumberInternal(recoveryPassword = recoveryPassword, newE164 = newE164)
  }

  suspend fun changeNumberWithoutRegistrationLock(sessionId: String, newE164: String): ChangeNumberResult {
    return changeNumberInternal(sessionId = sessionId, newE164 = newE164)
  }

  suspend fun changeNumberWithRegistrationLock(
    sessionId: String,
    newE164: String,
    pin: String,
    svrAuthCredentials: SvrAuthCredentialSet
  ): ChangeNumberResult {
    val masterKey: MasterKey

    try {
      masterKey = SvrRepository.restoreMasterKeyPreRegistration(svrAuthCredentials, pin)
    } catch (e: SvrWrongPinException) {
      return ChangeNumberResult.SvrWrongPin(e)
    } catch (e: SvrNoDataException) {
      return ChangeNumberResult.SvrNoData(e)
    } catch (e: IOException) {
      return ChangeNumberResult.UnknownError(e)
    }

    val registrationLock = masterKey.deriveRegistrationLock()
    return changeNumberInternal(sessionId = sessionId, registrationLock = registrationLock, newE164 = newE164)
  }

  /**
   * Sends a request to the service to change the phone number associated with this account.
   */
  private suspend fun changeNumberInternal(sessionId: String? = null, recoveryPassword: String? = null, registrationLock: String? = null, newE164: String): ChangeNumberResult {
    check((sessionId != null && recoveryPassword == null) || (sessionId == null && recoveryPassword != null))
    var completed = false
    var attempts = 0
    lateinit var result: NetworkResult<VerifyAccountResponse>

    while (!completed && attempts < 5) {
      Log.i(TAG, "Attempt #$attempts")
      val (request: ChangePhoneNumberRequest, metadata: PendingChangeNumberMetadata) = createChangeNumberRequest(
        sessionId = sessionId,
        recoveryPassword = recoveryPassword,
        newE164 = newE164,
        registrationLock = registrationLock
      )

      WaveStore.misc.setPendingChangeNumberMetadata(metadata)
      WaveStore.misc.lockChangeNumber()
      withContext(Dispatchers.IO) {
        result = WaveNetwork.account.changeNumber(request)
      }

      if (result is NetworkResult.StatusCodeError && result.code == 409) {
        val mismatchedDevices: MismatchedDevices? = result.parseJsonBody()
        if (mismatchedDevices != null) {
          messageSender.handleChangeNumberMismatchDevices(mismatchedDevices)
        }
        attempts++
      } else {
        completed = true
      }
    }

    if (result is NetworkResult.StatusCodeError) {
      WaveStore.misc.unlockChangeNumber()
    }

    Log.i(TAG, "Returning change number network result.")
    return ChangeNumberResult.from(
      result.map { accountRegistrationResponse: VerifyAccountResponse ->
        NumberChangeResult(
          uuid = accountRegistrationResponse.uuid,
          pni = accountRegistrationResponse.pni,
          storageCapable = accountRegistrationResponse.storageCapable,
          number = accountRegistrationResponse.number
        )
      }
    )
  }

  @WorkerThread
  private fun createChangeNumberRequest(
    sessionId: String? = null,
    recoveryPassword: String? = null,
    newE164: String,
    registrationLock: String? = null
  ): ChangeNumberRequestData {
    val selfIdentifier: String = WaveStore.account.requireAci().toString()
    val aciProtocolStore: WaveProtocolStore = AppDependencies.protocolStore.aci()

    val pniIdentity: IdentityKeyPair = IdentityKeyPair.generate()
    val deviceMessages = mutableListOf<OutgoingPushMessage>()
    val devicePniSignedPreKeys = mutableMapOf<Int, SignedPreKeyEntity>()
    val devicePniLastResortKyberPreKeys = mutableMapOf<Int, KyberPreKeyEntity>()
    val pniRegistrationIds = mutableMapOf<Int, Int>()
    val primaryDeviceId: Int = WaveServiceAddress.DEFAULT_DEVICE_ID

    val devices: List<Int> = listOf(primaryDeviceId) + aciProtocolStore.getSubDeviceSessions(selfIdentifier)

    devices
      .filter { it == primaryDeviceId || aciProtocolStore.containsSession(WaveProtocolAddress(selfIdentifier, it)) }
      .forEach { deviceId ->
        // Signed Prekeys
        val signedPreKeyRecord: SignedPreKeyRecord = if (deviceId == primaryDeviceId) {
          PreKeyUtil.generateAndStoreSignedPreKey(AppDependencies.protocolStore.pni(), WaveStore.account.pniPreKeys, pniIdentity.privateKey)
        } else {
          PreKeyUtil.generateSignedPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), pniIdentity.privateKey)
        }
        devicePniSignedPreKeys[deviceId] = SignedPreKeyEntity(signedPreKeyRecord.id, signedPreKeyRecord.keyPair.publicKey, signedPreKeyRecord.signature)

        // Last-resort kyber prekeys
        val lastResortKyberPreKeyRecord: KyberPreKeyRecord = if (deviceId == primaryDeviceId) {
          PreKeyUtil.generateAndStoreLastResortKyberPreKey(AppDependencies.protocolStore.pni(), WaveStore.account.pniPreKeys, pniIdentity.privateKey)
        } else {
          PreKeyUtil.generateLastResortKyberPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), pniIdentity.privateKey)
        }
        devicePniLastResortKyberPreKeys[deviceId] = KyberPreKeyEntity(lastResortKyberPreKeyRecord.id, lastResortKyberPreKeyRecord.keyPair.publicKey, lastResortKyberPreKeyRecord.signature)

        // Registration Ids
        var pniRegistrationId = -1

        while (pniRegistrationId < 0 || pniRegistrationIds.values.contains(pniRegistrationId)) {
          pniRegistrationId = KeyHelper.generateRegistrationId(false)
        }
        pniRegistrationIds[deviceId] = pniRegistrationId

        // Device Messages
        if (deviceId != primaryDeviceId) {
          val pniChangeNumber = SyncMessage.PniChangeNumber(
            identityKeyPair = pniIdentity.serialize().toByteString(),
            signedPreKey = signedPreKeyRecord.serialize().toByteString(),
            lastResortKyberPreKey = lastResortKyberPreKeyRecord.serialize().toByteString(),
            registrationId = pniRegistrationId,
            newE164 = newE164
          )

          deviceMessages += messageSender.getEncryptedSyncPniInitializeDeviceMessage(deviceId, pniChangeNumber)
        }
      }

    val request = ChangePhoneNumberRequest(
      sessionId,
      recoveryPassword,
      newE164,
      registrationLock,
      pniIdentity.publicKey,
      deviceMessages,
      devicePniSignedPreKeys.mapKeys { it.key.toString() },
      devicePniLastResortKyberPreKeys.mapKeys { it.key.toString() },
      pniRegistrationIds.mapKeys { it.key.toString() }
    )

    val metadata = PendingChangeNumberMetadata(
      previousPni = WaveStore.account.pni!!.toByteString(),
      pniIdentityKeyPair = pniIdentity.serialize().toByteString(),
      pniRegistrationId = pniRegistrationIds[primaryDeviceId]!!,
      pniSignedPreKeyId = devicePniSignedPreKeys[primaryDeviceId]!!.keyId,
      pniLastResortKyberPreKeyId = devicePniLastResortKyberPreKeys[primaryDeviceId]!!.keyId,
      previousE164 = WaveStore.account.requireE164(),
      newE164 = newE164
    )

    return ChangeNumberRequestData(request, metadata)
  }

  private data class ChangeNumberRequestData(val changeNumberRequest: ChangePhoneNumberRequest, val pendingChangeNumberMetadata: PendingChangeNumberMetadata)

  data class NumberChangeResult(
    val uuid: String,
    val pni: String,
    val storageCapable: Boolean,
    val number: String
  )
}

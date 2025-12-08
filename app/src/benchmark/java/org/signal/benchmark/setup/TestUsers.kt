package org.wave.benchmark.setup

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.WaveProtocolAddress
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.data.AccountRegistrationResult
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.waveservice.api.profiles.WaveServiceProfile
import org.whispersystems.waveservice.api.push.ServiceId.ACI
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import java.util.UUID

object TestUsers {

  private var generatedOthers: Int = 0
  private val TEST_E164 = "+15555550101"

  fun setupSelf(): Recipient {
    val application: Application = AppDependencies.application
    DeviceTransferBlockingInterceptor.getInstance().blockNetwork()

    PreferenceManager.getDefaultSharedPreferences(application).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(application, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(application, masterSecret)
    val preferences: SharedPreferences = application.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
    preferences.edit().putBoolean("passphrase_initialized", true).commit()

    WaveStore.account.generateAciIdentityKeyIfNecessary()
    WaveStore.account.generatePniIdentityKeyIfNecessary()

    runBlocking {
      val registrationData = RegistrationData(
        code = "123123",
        e164 = TEST_E164,
        password = Util.getSecret(18),
        registrationId = RegistrationRepository.getRegistrationId(),
        profileKey = RegistrationRepository.getProfileKey(TEST_E164),
        fcmToken = null,
        pniRegistrationId = RegistrationRepository.getPniRegistrationId(),
        recoveryPassword = "asdfasdfasdfasdf"
      )
      val remoteResult = AccountRegistrationResult(
        uuid = UUID.randomUUID().toString(),
        pni = UUID.randomUUID().toString(),
        storageCapable = false,
        number = TEST_E164,
        masterKey = null,
        pin = null,
        aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(WaveStore.account.aciIdentityKey, WaveStore.account.aciPreKeys),
        pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(WaveStore.account.aciIdentityKey, WaveStore.account.pniPreKeys),
        reRegistration = false
      )
      val localRegistrationData = LocalRegistrationMetadataUtil.createLocalRegistrationMetadata(WaveStore.account.aciIdentityKey, WaveStore.account.pniIdentityKey, registrationData, remoteResult, false)
      RegistrationRepository.registerAccountLocally(application, localRegistrationData)
    }

    WaveStore.svr.optOut()
    RegistrationUtil.maybeMarkRegistrationComplete()
    WaveDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))

    return Recipient.self()
  }

  fun setupTestRecipient(): RecipientId {
    return setupTestRecipients(1).first()
  }

  fun setupTestRecipients(othersCount: Int): List<RecipientId> {
    val others = mutableListOf<RecipientId>()
    synchronized(this) {
      if (generatedOthers + othersCount !in 0 until 1000) {
        throw IllegalArgumentException("$othersCount must be between 0 and 1000")
      }

      for (i in generatedOthers until generatedOthers + othersCount) {
        val aci = ACI.from(UUID.randomUUID())
        val recipientId = RecipientId.from(WaveServiceAddress(aci, "+15555551%03d".format(i)))
        WaveDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
        WaveDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
        WaveDatabase.recipients.setCapabilities(recipientId, WaveServiceProfile.Capabilities(true, true))
        WaveDatabase.recipients.setProfileSharing(recipientId, true)
        WaveDatabase.recipients.markRegistered(recipientId, aci)
        val otherIdentity = IdentityKeyPair.generate()
        AppDependencies.protocolStore.aci().saveIdentity(WaveProtocolAddress(aci.toString(), 1), otherIdentity.publicKey)

        others += recipientId
      }

      generatedOthers += othersCount
    }

    return others
  }
}

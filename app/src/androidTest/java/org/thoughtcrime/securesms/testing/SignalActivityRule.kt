package org.thoughtcrime.securesms.testing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.wave.core.models.ServiceId.ACI
import org.wave.libwave.protocol.IdentityKey
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.WaveProtocolAddress
import org.thoughtcrime.securesms.WaveInstrumentationApplicationContext
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.NewAccount
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.data.AccountRegistrationResult
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.testing.GroupTestingUtils.asMember
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.waveservice.api.profiles.WaveServiceProfile
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import java.util.UUID

/**
 * Test rule to use that sets up the application in a mostly registered state. Enough so that most
 * activities should be launchable directly.
 *
 * To use: `@get:Rule val harness = WaveActivityRule()`
 */
class WaveActivityRule(private val othersCount: Int = 4, private val createGroup: Boolean = false) : ExternalResource() {

  val application: Application = AppDependencies.application
  private val TEST_E164 = "+15555550101"

  lateinit var context: Context
    private set
  lateinit var self: Recipient
    private set
  lateinit var others: List<RecipientId>
    private set
  lateinit var othersKeys: List<IdentityKeyPair>

  var group: GroupTestingUtils.TestGroupInfo? = null
    private set

  val inMemoryLogger: InMemoryLogger
    get() = (application as WaveInstrumentationApplicationContext).inMemoryLogger

  override fun before() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    self = setupSelf()

    val setupOthers = setupOthers()
    others = setupOthers.first
    othersKeys = setupOthers.second

    if (createGroup && others.size >= 2) {
      group = GroupTestingUtils.insertGroup(
        revision = 0,
        self.asMember(),
        others[0].asMember(),
        others[1].asMember()
      )
    }
  }

  private fun setupSelf(): Recipient {
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

    WaveStore.settings.isMessageNotificationsEnabled = false
    WaveStore.registration.restoreDecisionState = RestoreDecisionState.NewAccount

    return Recipient.self()
  }

  private fun setupOthers(): Pair<List<RecipientId>, List<IdentityKeyPair>> {
    val others = mutableListOf<RecipientId>()
    val othersKeys = mutableListOf<IdentityKeyPair>()

    if (othersCount !in 0 until 1000) {
      throw IllegalArgumentException("$othersCount must be between 0 and 1000")
    }

    for (i in 0 until othersCount) {
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
      othersKeys += otherIdentity
    }

    return others to othersKeys
  }

  inline fun <reified T : Activity> launchActivity(initIntent: Intent.() -> Unit = {}): ActivityScenario<T> {
    return androidx.test.core.app.launchActivity(Intent(context, T::class.java).apply(initIntent))
  }

  fun changeIdentityKey(recipient: Recipient, identityKey: IdentityKey = IdentityKeyPair.generate().publicKey) {
    AppDependencies.protocolStore.aci().saveIdentity(WaveProtocolAddress(recipient.requireServiceId().toString(), 0), identityKey)
  }

  fun getIdentity(recipient: Recipient): IdentityKey {
    return AppDependencies.protocolStore.aci().identities().getIdentity(WaveProtocolAddress(recipient.requireServiceId().toString(), 0))
  }

  fun setVerified(recipient: Recipient, status: IdentityTable.VerifiedStatus) {
    AppDependencies.protocolStore.aci().identities().setVerified(recipient.id, getIdentity(recipient), IdentityTable.VerifiedStatus.VERIFIED)
  }
}

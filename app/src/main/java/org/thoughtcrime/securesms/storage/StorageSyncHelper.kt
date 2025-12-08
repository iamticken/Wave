package org.thoughtcrime.securesms.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.wave.core.util.Base64.encodeWithPadding
import org.wave.core.util.SqlUtil
import org.wave.core.util.UuidUtil
import org.wave.core.util.logging.Log
import org.wave.core.util.toByteArray
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.getSubscriber
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.isUserManuallyCancelled
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.setSubscriber
import org.thoughtcrime.securesms.database.NotificationProfileTables
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.payments.Entropy
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.waveservice.api.push.UsernameLinkComponents
import org.whispersystems.waveservice.api.storage.WaveAccountRecord
import org.whispersystems.waveservice.api.storage.WaveContactRecord
import org.whispersystems.waveservice.api.storage.WaveStorageManifest
import org.whispersystems.waveservice.api.storage.WaveStorageRecord
import org.whispersystems.waveservice.api.storage.StorageId
import org.whispersystems.waveservice.api.storage.safeSetBackupsSubscriber
import org.whispersystems.waveservice.api.storage.safeSetPayments
import org.whispersystems.waveservice.api.storage.safeSetSubscriber
import org.whispersystems.waveservice.api.storage.toWaveAccountRecord
import org.whispersystems.waveservice.api.storage.toWaveStorageRecord
import org.whispersystems.waveservice.internal.storage.protos.AccountRecord
import org.whispersystems.waveservice.internal.storage.protos.OptionalBool
import java.util.Optional
import java.util.concurrent.TimeUnit

object StorageSyncHelper {
  private val TAG = Log.tag(StorageSyncHelper::class.java)

  val KEY_GENERATOR: StorageKeyGenerator = StorageKeyGenerator { Util.getSecretBytes(16) }

  private var keyGenerator = KEY_GENERATOR

  private val REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(2)

  /**
   * Given a list of all the local and remote keys you know about, this will return a result telling
   * you which keys are exclusively remote and which are exclusively local.
   *
   * @param remoteIds All remote keys available.
   * @param localIds  All local keys available.
   * @return An object describing which keys are exclusive to the remote data set and which keys are
   * exclusive to the local data set.
   */
  @JvmStatic
  fun findIdDifference(
    remoteIds: Collection<StorageId>,
    localIds: Collection<StorageId>
  ): IdDifferenceResult {
    val remoteByRawId: Map<String, StorageId> = remoteIds.associateBy { encodeWithPadding(it.raw) }
    val localByRawId: Map<String, StorageId> = localIds.associateBy { encodeWithPadding(it.raw) }

    var hasTypeMismatch = remoteByRawId.size != remoteIds.size || localByRawId.size != localIds.size

    val remoteOnlyRawIds: MutableSet<String> = (remoteByRawId.keys - localByRawId.keys).toMutableSet()
    val localOnlyRawIds: MutableSet<String> = (localByRawId.keys - remoteByRawId.keys).toMutableSet()
    val sharedRawIds: Set<String> = localByRawId.keys.intersect(remoteByRawId.keys)

    for (rawId in sharedRawIds) {
      val remote = remoteByRawId[rawId]!!
      val local = localByRawId[rawId]!!

      if (remote.type != local.type) {
        remoteOnlyRawIds.remove(rawId)
        localOnlyRawIds.remove(rawId)
        hasTypeMismatch = true
        Log.w(TAG, "Remote type ${remote.type} did not match local type ${local.type}!")
      }
    }

    val remoteOnlyKeys = remoteOnlyRawIds.mapNotNull { remoteByRawId[it] }
    val localOnlyKeys = localOnlyRawIds.mapNotNull { localByRawId[it] }

    return IdDifferenceResult(remoteOnlyKeys, localOnlyKeys, hasTypeMismatch)
  }

  @JvmStatic
  fun generateKey(): ByteArray {
    return keyGenerator.generate()
  }

  @JvmStatic
  @VisibleForTesting
  fun setTestKeyGenerator(testKeyGenerator: StorageKeyGenerator?) {
    keyGenerator = testKeyGenerator ?: KEY_GENERATOR
  }

  @JvmStatic
  fun profileKeyChanged(update: StorageRecordUpdate<WaveContactRecord>): Boolean {
    return update.old.proto.profileKey != update.new.proto.profileKey
  }

  @JvmStatic
  fun buildAccountRecord(context: Context, self: Recipient): WaveStorageRecord {
    var self = self
    var selfRecord: RecipientRecord? = WaveDatabase.recipients.getRecordForSync(self.id)
    val pinned: List<RecipientRecord> = WaveDatabase.threads.getPinnedRecipientIds()
      .mapNotNull { WaveDatabase.recipients.getRecordForSync(it) }

    val storyViewReceiptsState = if (WaveStore.story.viewedReceiptsEnabled) {
      OptionalBool.ENABLED
    } else {
      OptionalBool.DISABLED
    }

    if (self.storageId == null || (selfRecord != null && selfRecord.storageId == null)) {
      Log.w(TAG, "[buildAccountRecord] No storageId for self or record! Generating. (Self: ${self.storageId != null}, Record: ${selfRecord?.storageId != null})")
      WaveDatabase.recipients.updateStorageId(self.id, generateKey())
      self = self().fresh()
      selfRecord = WaveDatabase.recipients.getRecordForSync(self.id)
    }

    if (selfRecord == null) {
      Log.w(TAG, "[buildAccountRecord] Could not find a RecipientRecord for ourselves! ID: ${self.id}")
    } else if (!selfRecord.storageId.contentEquals(self.storageId)) {
      Log.w(TAG, "[buildAccountRecord] StorageId on RecipientRecord did not match self! ID: ${self.id}")
    }

    val storageId = selfRecord?.storageId ?: self.storageId

    val accountRecord = WaveAccountRecord.newBuilder(selfRecord?.syncExtras?.storageProto).apply {
      profileKey = self.profileKey?.toByteString() ?: ByteString.EMPTY
      givenName = self.profileName.givenName
      familyName = self.profileName.familyName
      avatarUrlPath = self.profileAvatar ?: ""
      noteToSelfArchived = selfRecord != null && selfRecord.syncExtras.isArchived
      noteToSelfMarkedUnread = selfRecord != null && selfRecord.syncExtras.isForcedUnread
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context)
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context)
      sealedSenderIndicators = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context)
      linkPreviews = WaveStore.settings.isLinkPreviewsEnabled
      unlistedPhoneNumber = WaveStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
      phoneNumberSharingMode = StorageSyncModels.localToRemotePhoneNumberSharingMode(WaveStore.phoneNumberPrivacy.phoneNumberSharingMode)
      pinnedConversations = StorageSyncModels.localToRemotePinnedConversations(pinned)
      preferContactAvatars = WaveStore.settings.isPreferSystemContactPhotos
      primarySendsSms = false
      universalExpireTimer = WaveStore.settings.universalExpireTimer
      preferredReactionEmoji = WaveStore.emoji.reactions
      displayBadgesOnProfile = WaveStore.inAppPayments.getDisplayBadgesOnProfile()
      subscriptionManuallyCancelled = isUserManuallyCancelled(InAppPaymentSubscriberRecord.Type.DONATION)
      keepMutedChatsArchived = WaveStore.settings.shouldKeepMutedChatsArchived()
      hasSetMyStoriesPrivacy = WaveStore.story.userHasBeenNotifiedAboutStories
      hasViewedOnboardingStory = WaveStore.story.userHasViewedOnboardingStory
      storiesDisabled = WaveStore.story.isFeatureDisabled
      storyViewReceiptsEnabled = storyViewReceiptsState
      hasSeenGroupStoryEducationSheet = WaveStore.story.userHasSeenGroupStoryEducationSheet
      hasCompletedUsernameOnboarding = WaveStore.uiHints.hasCompletedUsernameOnboarding()
      avatarColor = StorageSyncModels.localToRemoteAvatarColor(self.avatarColor)
      username = WaveStore.account.username ?: ""
      usernameLink = WaveStore.account.usernameLink?.let { linkComponents ->
        AccountRecord.UsernameLink(
          entropy = linkComponents.entropy.toByteString(),
          serverId = linkComponents.serverId.toByteArray().toByteString(),
          color = StorageSyncModels.localToRemoteUsernameColor(WaveStore.misc.usernameQrCodeColorScheme)
        )
      }

      hasBackup = WaveStore.backup.areBackupsEnabled && WaveStore.backup.hasBackupBeenUploaded
      backupTier = when {
        WaveStore.account.isLinkedDevice -> null
        WaveStore.backup.areBackupsEnabled && WaveStore.backup.backupTier != null -> getBackupLevelValue(WaveStore.backup.backupTier!!)
        WaveStore.backup.backupTierInternalOverride != null -> getBackupLevelValue(WaveStore.backup.backupTierInternalOverride!!)
        else -> null
      }

      notificationProfileManualOverride = getNotificationProfileManualOverride()

      getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)?.let {
        safeSetSubscriber(it.subscriberId.bytes.toByteString(), it.currency?.currencyCode ?: "")
      }

      getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)?.let {
        safeSetBackupsSubscriber(it.subscriberId.bytes.toByteString(), it.iapSubscriptionId)
      }

      safeSetPayments(WaveStore.payments.mobileCoinPaymentsEnabled(), Optional.ofNullable(WaveStore.payments.paymentsEntropy).map { obj: Entropy -> obj.bytes }.orElse(null))
    }

    return accountRecord.toWaveAccountRecord(StorageId.forAccount(storageId)).toWaveStorageRecord()
  }

  // TODO: Currently we don't have access to the private values of the BackupLevel. Update when it becomes available.
  private fun getBackupLevelValue(tier: MessageBackupTier): Long {
    return when (tier) {
      MessageBackupTier.FREE -> 200
      MessageBackupTier.PAID -> 201
    }
  }

  private fun getNotificationProfileManualOverride(): AccountRecord.NotificationProfileManualOverride {
    val profile = WaveDatabase.notificationProfiles.getProfile(WaveStore.notificationProfile.manuallyEnabledProfile)
    return if (profile != null && profile.deletedTimestampMs == 0L) {
      Log.i(TAG, "Setting a manually enabled profile ${profile.id}")
      // From [StorageService.proto], end timestamp should be unset if no timespan was chosen in the UI
      val endTimestamp = if (WaveStore.notificationProfile.manuallyEnabledUntil == Long.MAX_VALUE) 0 else WaveStore.notificationProfile.manuallyEnabledUntil
      AccountRecord.NotificationProfileManualOverride(
        enabled = AccountRecord.NotificationProfileManualOverride.ManuallyEnabled(
          id = UuidUtil.toByteArray(profile.notificationProfileId.uuid).toByteString(),
          endAtTimestampMs = endTimestamp
        )
      )
    } else if (WaveStore.notificationProfile.manuallyDisabledAt != 0L) {
      Log.i(TAG, "Setting a manually disabled profile ${WaveStore.notificationProfile.manuallyDisabledAt}")
      AccountRecord.NotificationProfileManualOverride(
        disabledAtTimestampMs = WaveStore.notificationProfile.manuallyDisabledAt
      )
    } else {
      AccountRecord.NotificationProfileManualOverride()
    }
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, updatedRecord: WaveAccountRecord, fetchProfile: Boolean) {
    val localRecord = buildAccountRecord(context, self).let { it.proto.account!!.toWaveAccountRecord(it.id) }
    applyAccountStorageSyncUpdates(context, self, StorageRecordUpdate(localRecord, updatedRecord), fetchProfile)
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, update: StorageRecordUpdate<WaveAccountRecord>, fetchProfile: Boolean) {
    WaveDatabase.recipients.applyStorageSyncAccountUpdate(update)

    TextSecurePreferences.setReadReceiptsEnabled(context, update.new.proto.readReceipts)
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.new.proto.typingIndicators)
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.new.proto.sealedSenderIndicators)
    WaveStore.settings.isLinkPreviewsEnabled = update.new.proto.linkPreviews
    WaveStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (update.new.proto.unlistedPhoneNumber) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
    WaveStore.phoneNumberPrivacy.phoneNumberSharingMode = StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.new.proto.phoneNumberSharingMode)
    WaveStore.settings.isPreferSystemContactPhotos = update.new.proto.preferContactAvatars
    WaveStore.payments.setEnabledAndEntropy(update.new.proto.payments?.enabled == true, Entropy.fromBytes(update.new.proto.payments?.entropy?.toByteArray()))
    WaveStore.settings.universalExpireTimer = update.new.proto.universalExpireTimer
    WaveStore.emoji.reactions = update.new.proto.preferredReactionEmoji
    WaveStore.inAppPayments.setDisplayBadgesOnProfile(update.new.proto.displayBadgesOnProfile)
    WaveStore.settings.setKeepMutedChatsArchived(update.new.proto.keepMutedChatsArchived)
    WaveStore.story.userHasBeenNotifiedAboutStories = update.new.proto.hasSetMyStoriesPrivacy
    WaveStore.story.userHasViewedOnboardingStory = update.new.proto.hasViewedOnboardingStory
    WaveStore.story.isFeatureDisabled = update.new.proto.storiesDisabled
    WaveStore.story.userHasSeenGroupStoryEducationSheet = update.new.proto.hasSeenGroupStoryEducationSheet
    WaveStore.uiHints.setHasCompletedUsernameOnboarding(update.new.proto.hasCompletedUsernameOnboarding)

    if (update.new.proto.storyViewReceiptsEnabled == OptionalBool.UNSET) {
      WaveStore.story.viewedReceiptsEnabled = update.new.proto.readReceipts
    } else {
      WaveStore.story.viewedReceiptsEnabled = update.new.proto.storyViewReceiptsEnabled == OptionalBool.ENABLED
    }

    val remoteSubscriber = StorageSyncModels.remoteToLocalDonorSubscriber(update.new.proto.subscriberId, update.new.proto.subscriberCurrencyCode)
    if (remoteSubscriber != null) {
      setSubscriber(remoteSubscriber)
    }

    val remoteBackupsSubscriber = StorageSyncModels.remoteToLocalBackupSubscriber(update.new.proto.backupSubscriberData)
    if (remoteBackupsSubscriber != null) {
      setSubscriber(remoteBackupsSubscriber)
    }

    if (update.new.proto.subscriptionManuallyCancelled && !update.old.proto.subscriptionManuallyCancelled) {
      WaveStore.inAppPayments.updateLocalStateForManualCancellation(InAppPaymentSubscriberRecord.Type.DONATION)
    }

    if (fetchProfile && update.new.proto.avatarUrlPath.isNotBlank()) {
      AppDependencies.jobManager.add(RetrieveProfileAvatarJob(self, update.new.proto.avatarUrlPath))
    }

    if (update.new.proto.username != update.old.proto.username) {
      WaveStore.account.username = update.new.proto.username
      WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      WaveStore.account.usernameSyncErrorCount = 0
    }

    if (update.new.proto.usernameLink != null) {
      WaveStore.account.usernameLink = UsernameLinkComponents(
        update.new.proto.usernameLink!!.entropy.toByteArray(),
        UuidUtil.parseOrThrow(update.new.proto.usernameLink!!.serverId.toByteArray())
      )

      WaveStore.misc.usernameQrCodeColorScheme = StorageSyncModels.remoteToLocalUsernameColor(update.new.proto.usernameLink!!.color)
    }

    if (update.new.proto.notificationProfileManualOverride != null) {
      if (update.new.proto.notificationProfileManualOverride!!.enabled != null) {
        Log.i(TAG, "Found a remote enabled notification override")
        val remoteProfile = update.new.proto.notificationProfileManualOverride!!.enabled!!
        val remoteId = UuidUtil.parseOrNull(remoteProfile.id)
        val remoteEndTime = if (remoteProfile.endAtTimestampMs == 0L) Long.MAX_VALUE else remoteProfile.endAtTimestampMs

        if (remoteId == null) {
          Log.w(TAG, "Remote notification profile id is not valid")
        } else {
          val query = SqlUtil.buildQuery("${NotificationProfileTables.NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", NotificationProfileId(remoteId))
          val localProfile = WaveDatabase.notificationProfiles.getProfile(query)

          if (localProfile == null) {
            Log.w(TAG, "Unable to find local notification profile with given remote id $remoteId")
          } else {
            Log.i(TAG, "Setting manually enabled profile to ${localProfile.id} ending at $remoteEndTime.")
            WaveStore.notificationProfile.manuallyEnabledProfile = localProfile.id
            WaveStore.notificationProfile.manuallyEnabledUntil = remoteEndTime
            WaveStore.notificationProfile.manuallyDisabledAt = 0L
          }
        }
      } else if (update.new.proto.notificationProfileManualOverride!!.disabledAtTimestampMs != null) {
        Log.i(TAG, "Found a remote disabled notification override for ${update.new.proto.notificationProfileManualOverride!!.disabledAtTimestampMs!!}")
        WaveStore.notificationProfile.manuallyEnabledProfile = 0
        WaveStore.notificationProfile.manuallyEnabledUntil = 0
        WaveStore.notificationProfile.manuallyDisabledAt = update.new.proto.notificationProfileManualOverride!!.disabledAtTimestampMs!!
      }
    }
  }

  @JvmStatic
  fun scheduleSyncForDataChange() {
    if (!WaveStore.registration.isRegistrationComplete) {
      Log.d(TAG, "Registration still ongoing. Ignore sync request.")
      return
    }
    AppDependencies.jobManager.add(StorageSyncJob.forLocalChange())
  }

  @JvmStatic
  fun scheduleRoutineSync() {
    val timeSinceLastSync = System.currentTimeMillis() - WaveStore.storageService.lastSyncTime

    if (timeSinceLastSync > REFRESH_INTERVAL && WaveStore.registration.isRegistrationComplete) {
      Log.d(TAG, "Scheduling a sync. Last sync was $timeSinceLastSync ms ago.")
      AppDependencies.jobManager.add(StorageSyncJob.forRemoteChange())
    } else {
      Log.d(TAG, "No need for sync. Last sync was $timeSinceLastSync ms ago.")
    }
  }

  class IdDifferenceResult(
    @JvmField val remoteOnlyIds: List<StorageId>,
    @JvmField val localOnlyIds: List<StorageId>,
    val hasTypeMismatches: Boolean
  ) {
    val isEmpty: Boolean
      get() = remoteOnlyIds.isEmpty() && localOnlyIds.isEmpty()

    override fun toString(): String {
      return "remoteOnly: ${remoteOnlyIds.size}, localOnly: ${localOnlyIds.size}, hasTypeMismatches: $hasTypeMismatches"
    }
  }

  class WriteOperationResult(
    @JvmField val manifest: WaveStorageManifest,
    @JvmField val inserts: List<WaveStorageRecord>,
    @JvmField val deletes: List<ByteArray>
  ) {
    val isEmpty: Boolean
      get() = inserts.isEmpty() && deletes.isEmpty()

    override fun toString(): String {
      return if (isEmpty) {
        "Empty"
      } else {
        "ManifestVersion: ${manifest.version}, Total Keys: ${manifest.storageIds.size}, Inserts: ${inserts.size}, Deletes: ${deletes.size}"
      }
    }
  }
}

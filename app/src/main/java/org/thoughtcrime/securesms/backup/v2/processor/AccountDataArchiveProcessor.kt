/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import android.content.Context
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString
import org.wave.core.util.UuidUtil
import org.wave.core.util.isNotNullOrBlank
import org.wave.core.util.logging.Log
import org.wave.core.util.toByteArray
import org.wave.libwave.zkgroup.backups.BackupLevel
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.database.restoreSelfFromBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreWallpaperAttachment
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.ChatStyle
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.backup.v2.util.ChatStyleConverter
import org.thoughtcrime.securesms.backup.v2.util.isValid
import org.thoughtcrime.securesms.backup.v2.util.isValidUsername
import org.thoughtcrime.securesms.backup.v2.util.parseChatWallpaper
import org.thoughtcrime.securesms.backup.v2.util.toLocal
import org.thoughtcrime.securesms.backup.v2.util.toLocalAttachment
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.ProfileUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.webrtc.CallDataMode
import org.whispersystems.waveservice.api.push.UsernameLinkComponents
import org.whispersystems.waveservice.api.storage.IAPSubscriptionId.AppleIAPOriginalTransactionId
import org.whispersystems.waveservice.api.storage.IAPSubscriptionId.GooglePlayBillingPurchaseToken
import org.whispersystems.waveservice.api.subscriptions.SubscriberId
import java.util.Currency
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Handles importing/exporting [AccountData] frames for an archive.
 */
object AccountDataArchiveProcessor {

  private val TAG = Log.tag(AccountDataArchiveProcessor::class)

  fun export(db: WaveDatabase, waveStore: WaveStore, exportState: ExportState, emitter: BackupFrameEmitter) {
    val context = AppDependencies.application

    val selfId = db.recipientTable.getByAci(waveStore.accountValues.aci!!).get()
    val selfRecord = db.recipientTable.getRecordForSync(selfId)!!

    val donationCurrency = waveStore.inAppPaymentValues.getRecurringDonationCurrency()
    val donationSubscriber = db.inAppPaymentSubscriberTable.getByCurrencyCode(donationCurrency.currencyCode)

    val chatColors = WaveStore.chatColors.chatColors
    val chatWallpaper = WaveStore.wallpaper.currentRawWallpaper

    val backupSubscriberRecord = db.inAppPaymentSubscriberTable.getBackupsSubscriber()

    val screenLockTimeoutSeconds = waveStore.settingsValues.screenLockTimeout
    val screenLockTimeoutMinutes = if (screenLockTimeoutSeconds > 0) {
      screenLockTimeoutSeconds.seconds.inWholeMinutes.toInt()
    } else {
      null
    }

    val mobileAutoDownload = TextSecurePreferences.getMobileMediaDownloadAllowed(context)
    val wifiAutoDownload = TextSecurePreferences.getWifiMediaDownloadAllowed(context)

    emitter.emit(
      Frame(
        account = AccountData(
          profileKey = selfRecord.profileKey?.toByteString() ?: EMPTY,
          givenName = selfRecord.waveProfileName.givenName,
          familyName = selfRecord.waveProfileName.familyName,
          avatarUrlPath = selfRecord.waveProfileAvatar ?: "",
          svrPin = WaveStore.svr.pin ?: "",
          username = selfRecord.username?.takeIf { it.isValidUsername() },
          usernameLink = if (selfRecord.username.isNotNullOrBlank() && waveStore.accountValues.usernameLink != null) {
            AccountData.UsernameLink(
              entropy = waveStore.accountValues.usernameLink?.entropy?.toByteString() ?: EMPTY,
              serverId = waveStore.accountValues.usernameLink?.serverId?.toByteArray()?.toByteString() ?: EMPTY,
              color = waveStore.miscValues.usernameQrCodeColorScheme.toRemoteUsernameColor()
            )
          } else {
            null
          },
          accountSettings = AccountData.AccountSettings(
            storyViewReceiptsEnabled = waveStore.storyValues.viewedReceiptsEnabled,
            typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context),
            readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context),
            sealedSenderIndicators = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
            linkPreviews = waveStore.settingsValues.isLinkPreviewsEnabled,
            notDiscoverableByPhoneNumber = waveStore.phoneNumberPrivacyValues.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE,
            phoneNumberSharingMode = waveStore.phoneNumberPrivacyValues.phoneNumberSharingMode.toRemotePhoneNumberSharingMode(),
            preferContactAvatars = waveStore.settingsValues.isPreferSystemContactPhotos,
            universalExpireTimerSeconds = waveStore.settingsValues.universalExpireTimer,
            preferredReactionEmoji = waveStore.emojiValues.rawReactions,
            storiesDisabled = waveStore.storyValues.isFeatureDisabled,
            hasViewedOnboardingStory = waveStore.storyValues.userHasViewedOnboardingStory,
            hasSetMyStoriesPrivacy = waveStore.storyValues.userHasBeenNotifiedAboutStories,
            keepMutedChatsArchived = waveStore.settingsValues.shouldKeepMutedChatsArchived(),
            displayBadgesOnProfile = waveStore.inAppPaymentValues.getDisplayBadgesOnProfile(),
            hasSeenGroupStoryEducationSheet = waveStore.storyValues.userHasSeenGroupStoryEducationSheet,
            hasCompletedUsernameOnboarding = waveStore.uiHintValues.hasCompletedUsernameOnboarding(),
            customChatColors = db.chatColorsTable.getSavedChatColors().toRemoteChatColors().also { colors -> exportState.customChatColorIds.addAll(colors.map { it.id }) },
            optimizeOnDeviceStorage = waveStore.backupValues.optimizeStorage,
            backupTier = waveStore.backupValues.backupTier.toRemoteBackupTier(),
            defaultSentMediaQuality = waveStore.settingsValues.sentMediaQuality.toRemoteSentMediaQuality(),
            autoDownloadSettings = AccountData.AutoDownloadSettings(
              images = getRemoteAutoDownloadOption("image", mobileAutoDownload, wifiAutoDownload),
              audio = getRemoteAutoDownloadOption("audio", mobileAutoDownload, wifiAutoDownload),
              video = getRemoteAutoDownloadOption("video", mobileAutoDownload, wifiAutoDownload),
              documents = getRemoteAutoDownloadOption("documents", mobileAutoDownload, wifiAutoDownload)
            ),
            screenLockTimeoutMinutes = screenLockTimeoutMinutes,
            pinReminders = waveStore.pinValues.arePinRemindersEnabled(),
            appTheme = waveStore.settingsValues.theme.toRemoteAppTheme(),
            callsUseLessDataSetting = waveStore.settingsValues.callDataMode.toRemoteCallsUseLessDataSetting(),
            defaultChatStyle = ChatStyleConverter.constructRemoteChatStyle(
              db = db,
              chatColors = chatColors,
              chatColorId = chatColors?.id?.takeIf { it.isValid(exportState) } ?: ChatColors.Id.NotSet,
              chatWallpaper = chatWallpaper
            )
          ),
          donationSubscriberData = donationSubscriber?.toSubscriberData(waveStore.inAppPaymentValues.isDonationSubscriptionManuallyCancelled()),
          backupsSubscriberData = backupSubscriberRecord?.toIAPSubscriberData(),
          androidSpecificSettings = AccountData.AndroidSpecificSettings(
            useSystemEmoji = waveStore.settingsValues.isPreferSystemEmoji,
            screenshotSecurity = TextSecurePreferences.isScreenSecurityEnabled(context),
            navigationBarSize = waveStore.settingsValues.useCompactNavigationBar.toRemoteNavigationBarSize()
          ).takeUnless { Environment.IS_INSTRUMENTATION && WaveStore.backup.importedEmptyAndroidSettings },
          bioText = selfRecord.about ?: "",
          bioEmoji = selfRecord.aboutEmoji ?: ""
        )
      )
    )
  }

  fun import(accountData: AccountData, selfId: RecipientId, importState: ImportState) {
    WaveDatabase.recipients.restoreSelfFromBackup(accountData, selfId)

    WaveStore.account.setRegistered(true)
    if (accountData.svrPin.isNotBlank()) {
      WaveStore.svr.setPin(accountData.svrPin)
    }

    val context = AppDependencies.application
    val settings = accountData.accountSettings

    if (settings != null) {
      importSettings(context, settings, importState)
    }

    if (accountData.androidSpecificSettings != null) {
      WaveStore.settings.isPreferSystemEmoji = accountData.androidSpecificSettings.useSystemEmoji
      TextSecurePreferences.setScreenSecurityEnabled(context, accountData.androidSpecificSettings.screenshotSecurity)
      WaveStore.settings.useCompactNavigationBar = accountData.androidSpecificSettings.navigationBarSize.toLocalNavigationBarSize()
    } else if (Environment.IS_INSTRUMENTATION) {
      WaveStore.backup.importedEmptyAndroidSettings = true
    }

    if (accountData.bioText.isNotBlank() || accountData.bioEmoji.isNotBlank()) {
      WaveDatabase.recipients.setAbout(selfId, accountData.bioText.takeIf { it.isNotBlank() }, accountData.bioEmoji.takeIf { it.isNotBlank() })
    }

    if (accountData.donationSubscriberData != null) {
      if (accountData.donationSubscriberData.subscriberId.size > 0) {
        val remoteSubscriberId = SubscriberId.fromBytes(accountData.donationSubscriberData.subscriberId.toByteArray())
        val localSubscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)

        val subscriber = InAppPaymentSubscriberRecord(
          subscriberId = remoteSubscriberId,
          currency = Currency.getInstance(accountData.donationSubscriberData.currencyCode),
          type = InAppPaymentSubscriberRecord.Type.DONATION,
          requiresCancel = localSubscriber?.requiresCancel ?: accountData.donationSubscriberData.manuallyCancelled,
          paymentMethodType = InAppPaymentsRepository.getLatestPaymentMethodType(InAppPaymentSubscriberRecord.Type.DONATION),
          iapSubscriptionId = null
        )

        InAppPaymentsRepository.setSubscriber(subscriber)
      }

      if (accountData.donationSubscriberData.manuallyCancelled) {
        WaveStore.inAppPayments.updateLocalStateForManualCancellation(InAppPaymentSubscriberRecord.Type.DONATION)
      }
    }

    if (accountData.backupsSubscriberData != null && accountData.backupsSubscriberData.subscriberId.size > 0 && (accountData.backupsSubscriberData.purchaseToken != null || accountData.backupsSubscriberData.originalTransactionId != null)) {
      val remoteSubscriberId = SubscriberId.fromBytes(accountData.backupsSubscriberData.subscriberId.toByteArray())
      val localSubscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)

      val subscriber = InAppPaymentSubscriberRecord(
        subscriberId = remoteSubscriberId,
        currency = localSubscriber?.currency,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = localSubscriber?.requiresCancel ?: false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN,
        iapSubscriptionId = if (accountData.backupsSubscriberData.purchaseToken != null) {
          GooglePlayBillingPurchaseToken(accountData.backupsSubscriberData.purchaseToken)
        } else {
          AppleIAPOriginalTransactionId(accountData.backupsSubscriberData.originalTransactionId!!)
        }
      )

      InAppPaymentsRepository.setSubscriber(subscriber)
    }

    if (accountData.avatarUrlPath.isNotEmpty()) {
      AppDependencies.jobManager.add(RetrieveProfileAvatarJob(Recipient.self().fresh(), accountData.avatarUrlPath))
    }

    if (accountData.usernameLink != null) {
      WaveStore.account.usernameLink = UsernameLinkComponents(
        accountData.usernameLink.entropy.toByteArray(),
        UuidUtil.parseOrThrow(accountData.usernameLink.serverId.toByteArray())
      )
      WaveStore.misc.usernameQrCodeColorScheme = accountData.usernameLink.color.toLocalUsernameColor()
    } else {
      WaveStore.account.usernameLink = null
    }

    WaveDatabase.runPostSuccessfulTransaction { ProfileUtil.handleSelfProfileKeyChange() }

    Recipient.self().live().refresh()
  }

  private fun importSettings(context: Context, settings: AccountData.AccountSettings, importState: ImportState) {
    TextSecurePreferences.setReadReceiptsEnabled(context, settings.readReceipts)
    TextSecurePreferences.setTypingIndicatorsEnabled(context, settings.typingIndicators)
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, settings.sealedSenderIndicators)
    WaveStore.settings.isLinkPreviewsEnabled = settings.linkPreviews
    WaveStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (settings.notDiscoverableByPhoneNumber) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
    WaveStore.phoneNumberPrivacy.phoneNumberSharingMode = settings.phoneNumberSharingMode.toLocalPhoneNumberMode()
    WaveStore.settings.isPreferSystemContactPhotos = settings.preferContactAvatars
    WaveStore.settings.universalExpireTimer = settings.universalExpireTimerSeconds
    WaveStore.emoji.reactions = settings.preferredReactionEmoji
    WaveStore.inAppPayments.setDisplayBadgesOnProfile(settings.displayBadgesOnProfile)
    WaveStore.settings.setKeepMutedChatsArchived(settings.keepMutedChatsArchived)
    WaveStore.story.userHasBeenNotifiedAboutStories = settings.hasSetMyStoriesPrivacy
    WaveStore.story.userHasViewedOnboardingStory = settings.hasViewedOnboardingStory
    WaveStore.story.isFeatureDisabled = settings.storiesDisabled
    WaveStore.story.userHasSeenGroupStoryEducationSheet = settings.hasSeenGroupStoryEducationSheet
    WaveStore.story.viewedReceiptsEnabled = settings.storyViewReceiptsEnabled ?: settings.readReceipts
    WaveStore.backup.optimizeStorage = settings.optimizeOnDeviceStorage
    WaveStore.backup.backupTier = settings.backupTier?.toLocalBackupTier()
    WaveStore.settings.sentMediaQuality = settings.defaultSentMediaQuality.toLocalSentMediaQuality()
    WaveStore.settings.setTheme(settings.appTheme.toLocalTheme())
    WaveStore.settings.setCallDataMode(settings.callsUseLessDataSetting.toLocalCallDataMode())

    if (settings.autoDownloadSettings != null) {
      val mobileDownloadSet = settings.autoDownloadSettings.toLocalAutoDownloadSet(AccountData.AutoDownloadSettings.AutoDownloadOption.WIFI_AND_CELLULAR)
      val wifiDownloadSet = settings.autoDownloadSettings.toLocalAutoDownloadSet(AccountData.AutoDownloadSettings.AutoDownloadOption.WIFI)

      TextSecurePreferences.getSharedPreferences(context).edit().apply {
        putStringSet(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF, mobileDownloadSet)
        putStringSet(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF, wifiDownloadSet)
        apply()
      }
    }

    if (settings.screenLockTimeoutMinutes != null) {
      WaveStore.settings.screenLockTimeout = settings.screenLockTimeoutMinutes.minutes.inWholeSeconds
    }

    if (settings.pinReminders != null) {
      WaveStore.pin.setPinRemindersEnabled(settings.pinReminders)
    }

    settings.customChatColors
      .mapNotNull { chatColor ->
        val id = ChatColors.Id.forLongValue(chatColor.id)
        when {
          chatColor.solid != null -> {
            ChatColors.forColor(id, chatColor.solid)
          }
          chatColor.gradient != null -> {
            ChatColors.forGradient(
              id,
              ChatColors.LinearGradient(
                degrees = chatColor.gradient.angle.toFloat(),
                colors = chatColor.gradient.colors.toIntArray(),
                positions = chatColor.gradient.positions.toFloatArray()
              )
            )
          }
          else -> null
        }
      }
      .forEach { chatColor ->
        // We need to use the "NotSet" chatId so that this operation is treated as an insert rather than an update
        val saved = WaveDatabase.chatColors.saveChatColors(chatColor.withId(ChatColors.Id.NotSet))
        importState.remoteToLocalColorId[chatColor.id.longValue] = saved.id.longValue
      }

    if (settings.defaultChatStyle != null) {
      val chatColors = settings.defaultChatStyle.toLocal(importState)
      WaveStore.chatColors.chatColors = chatColors

      val wallpaperAttachmentId: AttachmentId? = settings.defaultChatStyle.wallpaperPhoto?.let { filePointer ->
        filePointer.toLocalAttachment()?.let {
          WaveDatabase.attachments.restoreWallpaperAttachment(it)
        }
      }

      WaveStore.wallpaper.wallpaper = settings.defaultChatStyle.parseChatWallpaper(wallpaperAttachmentId)
    } else {
      WaveStore.chatColors.chatColors = null
      WaveStore.wallpaper.wallpaper = null
    }

    if (settings.preferredReactionEmoji.isNotEmpty()) {
      WaveStore.emoji.reactions = settings.preferredReactionEmoji
    }

    if (settings.hasCompletedUsernameOnboarding) {
      WaveStore.uiHints.setHasCompletedUsernameOnboarding(true)
    }
  }

  private fun PhoneNumberPrivacyValues.PhoneNumberSharingMode.toRemotePhoneNumberSharingMode(): AccountData.PhoneNumberSharingMode {
    return when (this) {
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.DEFAULT -> AccountData.PhoneNumberSharingMode.EVERYBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY -> AccountData.PhoneNumberSharingMode.EVERYBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY -> AccountData.PhoneNumberSharingMode.NOBODY
    }
  }

  private fun AccountData.PhoneNumberSharingMode.toLocalPhoneNumberMode(): PhoneNumberPrivacyValues.PhoneNumberSharingMode {
    return when (this) {
      AccountData.PhoneNumberSharingMode.UNKNOWN -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY
      AccountData.PhoneNumberSharingMode.EVERYBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY
      AccountData.PhoneNumberSharingMode.NOBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY
    }
  }

  private fun AccountData.UsernameLink.Color?.toLocalUsernameColor(): UsernameQrCodeColorScheme {
    return when (this) {
      AccountData.UsernameLink.Color.BLUE -> UsernameQrCodeColorScheme.Blue
      AccountData.UsernameLink.Color.WHITE -> UsernameQrCodeColorScheme.White
      AccountData.UsernameLink.Color.GREY -> UsernameQrCodeColorScheme.Grey
      AccountData.UsernameLink.Color.OLIVE -> UsernameQrCodeColorScheme.Tan
      AccountData.UsernameLink.Color.GREEN -> UsernameQrCodeColorScheme.Green
      AccountData.UsernameLink.Color.ORANGE -> UsernameQrCodeColorScheme.Orange
      AccountData.UsernameLink.Color.PINK -> UsernameQrCodeColorScheme.Pink
      AccountData.UsernameLink.Color.PURPLE -> UsernameQrCodeColorScheme.Purple
      else -> UsernameQrCodeColorScheme.Blue
    }
  }

  private fun UsernameQrCodeColorScheme.toRemoteUsernameColor(): AccountData.UsernameLink.Color {
    return when (this) {
      UsernameQrCodeColorScheme.Blue -> AccountData.UsernameLink.Color.BLUE
      UsernameQrCodeColorScheme.White -> AccountData.UsernameLink.Color.WHITE
      UsernameQrCodeColorScheme.Grey -> AccountData.UsernameLink.Color.GREY
      UsernameQrCodeColorScheme.Tan -> AccountData.UsernameLink.Color.OLIVE
      UsernameQrCodeColorScheme.Green -> AccountData.UsernameLink.Color.GREEN
      UsernameQrCodeColorScheme.Orange -> AccountData.UsernameLink.Color.ORANGE
      UsernameQrCodeColorScheme.Pink -> AccountData.UsernameLink.Color.PINK
      UsernameQrCodeColorScheme.Purple -> AccountData.UsernameLink.Color.PURPLE
    }
  }

  /**
   * This method only supports donations subscriber data, and assumes there is a currency code available.
   */
  private fun InAppPaymentSubscriberRecord.toSubscriberData(manuallyCancelled: Boolean): AccountData.SubscriberData {
    val subscriberId = subscriberId.bytes.toByteString()
    val currencyCode = currency!!.currencyCode
    return AccountData.SubscriberData(subscriberId = subscriberId, currencyCode = currencyCode, manuallyCancelled = manuallyCancelled)
  }

  private fun InAppPaymentSubscriberRecord?.toIAPSubscriberData(): AccountData.IAPSubscriberData? {
    if (this == null) {
      return null
    }

    val builder = AccountData.IAPSubscriberData.Builder()
      .subscriberId(this.subscriberId.bytes.toByteString())

    if (this.iapSubscriptionId?.purchaseToken != null) {
      builder.purchaseToken(this.iapSubscriptionId.purchaseToken)
    } else if (this.iapSubscriptionId?.originalTransactionId != null) {
      builder.originalTransactionId(this.iapSubscriptionId.originalTransactionId)
    }

    return builder.build()
  }

  private fun List<ChatColors>.toRemoteChatColors(): List<ChatStyle.CustomChatColor> {
    return this
      .mapNotNull { local ->
        if (local.linearGradient != null) {
          ChatStyle.CustomChatColor(
            id = local.id.longValue,
            gradient = ChatStyle.Gradient(
              angle = local.linearGradient.degrees.toInt(),
              colors = local.linearGradient.colors.toList(),
              positions = local.linearGradient.positions.toList()
            )
          )
        } else if (local.singleColor != null) {
          ChatStyle.CustomChatColor(
            id = local.id.longValue,
            solid = local.singleColor
          )
        } else {
          Log.w(TAG, "Invalid custom color (id = ${local.id}, no gradient or solid color!")
          null
        }
      }
  }

  private fun MessageBackupTier?.toRemoteBackupTier(): Long? {
    return when (this) {
      MessageBackupTier.FREE -> BackupLevel.FREE.value.toLong()
      MessageBackupTier.PAID -> BackupLevel.PAID.value.toLong()
      null -> null
    }
  }

  private fun Long?.toLocalBackupTier(): MessageBackupTier? {
    return when (this) {
      BackupLevel.FREE.value.toLong() -> MessageBackupTier.FREE
      BackupLevel.PAID.value.toLong() -> MessageBackupTier.PAID
      else -> null
    }
  }

  private fun org.thoughtcrime.securesms.mms.SentMediaQuality.toRemoteSentMediaQuality(): AccountData.SentMediaQuality {
    return when (this) {
      org.thoughtcrime.securesms.mms.SentMediaQuality.STANDARD -> AccountData.SentMediaQuality.STANDARD
      org.thoughtcrime.securesms.mms.SentMediaQuality.HIGH -> AccountData.SentMediaQuality.HIGH
    }
  }

  private fun AccountData.SentMediaQuality?.toLocalSentMediaQuality(): org.thoughtcrime.securesms.mms.SentMediaQuality {
    return when (this) {
      AccountData.SentMediaQuality.HIGH -> org.thoughtcrime.securesms.mms.SentMediaQuality.HIGH
      AccountData.SentMediaQuality.STANDARD -> org.thoughtcrime.securesms.mms.SentMediaQuality.STANDARD
      AccountData.SentMediaQuality.UNKNOWN_QUALITY -> org.thoughtcrime.securesms.mms.SentMediaQuality.STANDARD
      null -> org.thoughtcrime.securesms.mms.SentMediaQuality.STANDARD
    }
  }

  private fun getRemoteAutoDownloadOption(mediaType: String, mobileSet: Set<String>, wifiSet: Set<String>): AccountData.AutoDownloadSettings.AutoDownloadOption {
    return when {
      mobileSet.contains(mediaType) -> AccountData.AutoDownloadSettings.AutoDownloadOption.WIFI_AND_CELLULAR
      wifiSet.contains(mediaType) -> AccountData.AutoDownloadSettings.AutoDownloadOption.WIFI
      else -> AccountData.AutoDownloadSettings.AutoDownloadOption.NEVER
    }
  }

  private fun AccountData.AutoDownloadSettings.toLocalAutoDownloadSet(option: AccountData.AutoDownloadSettings.AutoDownloadOption): Set<String> {
    val out = mutableSetOf<String>()
    if (this.images == option) {
      out += "image"
    }
    if (this.audio == option) {
      out += "audio"
    }
    if (this.video == option) {
      out += "video"
    }
    if (this.documents == option) {
      out += "documents"
    }
    return out
  }

  private fun Boolean.toRemoteNavigationBarSize(): AccountData.AndroidSpecificSettings.NavigationBarSize {
    return if (this) {
      AccountData.AndroidSpecificSettings.NavigationBarSize.COMPACT
    } else {
      AccountData.AndroidSpecificSettings.NavigationBarSize.NORMAL
    }
  }

  private fun AccountData.AndroidSpecificSettings.NavigationBarSize.toLocalNavigationBarSize(): Boolean {
    return when (this) {
      AccountData.AndroidSpecificSettings.NavigationBarSize.COMPACT -> true
      AccountData.AndroidSpecificSettings.NavigationBarSize.NORMAL -> false
      AccountData.AndroidSpecificSettings.NavigationBarSize.UNKNOWN_BAR_SIZE -> false
    }
  }

  private fun SettingsValues.Theme.toRemoteAppTheme(): AccountData.AppTheme {
    return when (this) {
      SettingsValues.Theme.SYSTEM -> AccountData.AppTheme.SYSTEM
      SettingsValues.Theme.LIGHT -> AccountData.AppTheme.LIGHT
      SettingsValues.Theme.DARK -> AccountData.AppTheme.DARK
    }
  }

  private fun AccountData.AppTheme.toLocalTheme(): SettingsValues.Theme {
    return when (this) {
      AccountData.AppTheme.SYSTEM -> SettingsValues.Theme.SYSTEM
      AccountData.AppTheme.LIGHT -> SettingsValues.Theme.LIGHT
      AccountData.AppTheme.DARK -> SettingsValues.Theme.DARK
      AccountData.AppTheme.UNKNOWN_APP_THEME -> SettingsValues.Theme.SYSTEM
    }
  }

  private fun CallDataMode.toRemoteCallsUseLessDataSetting(): AccountData.CallsUseLessDataSetting {
    return when (this) {
      CallDataMode.LOW_ALWAYS -> AccountData.CallsUseLessDataSetting.WIFI_AND_MOBILE_DATA
      CallDataMode.HIGH_ON_WIFI -> AccountData.CallsUseLessDataSetting.MOBILE_DATA_ONLY
      CallDataMode.HIGH_ALWAYS -> AccountData.CallsUseLessDataSetting.NEVER
    }
  }

  private fun AccountData.CallsUseLessDataSetting.toLocalCallDataMode(): CallDataMode {
    return when (this) {
      AccountData.CallsUseLessDataSetting.WIFI_AND_MOBILE_DATA -> CallDataMode.LOW_ALWAYS
      AccountData.CallsUseLessDataSetting.MOBILE_DATA_ONLY -> CallDataMode.HIGH_ON_WIFI
      AccountData.CallsUseLessDataSetting.NEVER -> CallDataMode.HIGH_ALWAYS
      AccountData.CallsUseLessDataSetting.UNKNOWN_CALL_DATA_SETTING -> CallDataMode.HIGH_ALWAYS
    }
  }
}

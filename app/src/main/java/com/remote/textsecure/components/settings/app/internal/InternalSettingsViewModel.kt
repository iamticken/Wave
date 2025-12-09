package org.thoughtcrime.securesms.components.settings.app.internal

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import org.wave.ringrtc.CallManager
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.jobs.StoryOnboardingDownloadJob
import org.thoughtcrime.securesms.keyvalue.InternalValues
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.livedata.Store

class InternalSettingsViewModel(private val repository: InternalSettingsRepository) : ViewModel() {
  private val preferenceDataStore = WaveStore.getPreferenceDataStore()

  private val store = Store(getState())

  init {
    repository.getEmojiVersionInfo { version ->
      store.update { it.copy(emojiVersion = version) }
    }

    val pendingOneTimeDonation: Observable<Boolean> = WaveStore.inAppPayments.observablePendingOneTimeDonation
      .distinctUntilChanged()
      .map { it.isPresent }

    store.update(pendingOneTimeDonation) { pending, state ->
      state.copy(hasPendingOneTimeDonation = pending)
    }
  }

  val state: LiveData<InternalSettingsState> = store.stateLiveData

  fun setSeeMoreUserDetails(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.RECIPIENT_DETAILS, enabled)
    refresh()
  }

  fun setShakeToReport(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.SHAKE_TO_REPORT, enabled)
    refresh()
  }

  fun setShowMediaArchiveStateHint(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.SHOW_ARCHIVE_STATE_HINT, enabled)
    refresh()
  }

  fun setDisableStorageService(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.DISABLE_STORAGE_SERVICE, enabled)
    refresh()
  }

  fun setGv2ForceInvites(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_FORCE_INVITES, enabled)
    refresh()
  }

  fun setGv2IgnoreP2PChanges(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_IGNORE_P2P_CHANGES, enabled)
    refresh()
  }

  fun setAllowCensorshipSetting(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.ALLOW_CENSORSHIP_SETTING, enabled)
    refresh()
  }

  fun setForceWebsocketMode(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.FORCE_WEBSOCKET_MODE, enabled)
    refresh()
  }

  fun resetPnpInitializedState() {
    WaveStore.misc.hasPniInitializedDevices = false
    refresh()
  }

  fun setUseBuiltInEmoji(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.FORCE_BUILT_IN_EMOJI, enabled)
    refresh()
  }

  fun setRemoveSenderKeyMinimum(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.REMOVE_SENDER_KEY_MINIMUM, enabled)
    refresh()
  }

  fun setDelayResends(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.DELAY_RESENDS, enabled)
    refresh()
  }

  fun setInternalGroupCallingServer(server: String?) {
    preferenceDataStore.putString(InternalValues.CALLING_SERVER, server)
    refresh()
  }

  fun setInternalCallingDataMode(dataMode: CallManager.DataMode) {
    preferenceDataStore.putInt(InternalValues.CALLING_DATA_MODE, dataMode.ordinal)
    refresh()
  }

  fun setInternalCallingDisableTelecom(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_DISABLE_TELECOM, enabled)
    refresh()
  }

  fun setInternalCallingSetAudioConfig(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_SET_AUDIO_CONFIG, enabled)
    refresh()
  }

  fun setInternalCallingUseOboeAdm(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_OBOE_ADM, enabled)
    refresh()
  }

  fun setInternalCallingUseSoftwareAec(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_SOFTWARE_AEC, enabled)
    refresh()
  }

  fun setInternalCallingUseSoftwareNs(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_SOFTWARE_NS, enabled)
    refresh()
  }

  fun setInternalCallingUseInputLowLatency(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_INPUT_LOW_LATENCY, enabled)
    refresh()
  }

  fun setInternalCallingUseInputVoiceComm(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_INPUT_VOICE_COMM, enabled)
    refresh()
  }

  fun setUseConversationItemV2Media(enabled: Boolean) {
    WaveStore.internal.useConversationItemV2Media = enabled
    refresh()
  }

  fun setHevcEncoding(enabled: Boolean) {
    WaveStore.internal.hevcEncoding = enabled
    refresh()
  }

  fun addSampleReleaseNote() {
    repository.addSampleReleaseNote()
  }

  fun addRemoteDonateMegaphone() {
    repository.addRemoteMegaphone(RemoteMegaphoneRecord.ActionId.DONATE)
  }

  fun addRemoteDonateFriendMegaphone() {
    repository.addRemoteMegaphone(RemoteMegaphoneRecord.ActionId.DONATE_FOR_FRIEND)
  }

  fun enqueueSubscriptionRedemption() {
    repository.enqueueSubscriptionRedemption()
  }

  fun refresh() {
    store.update { getState().copy(emojiVersion = it.emojiVersion) }
  }

  private fun getState() = InternalSettingsState(
    seeMoreUserDetails = WaveStore.internal.recipientDetails,
    shakeToReport = WaveStore.internal.shakeToReport,
    showArchiveStateHint = WaveStore.internal.showArchiveStateHint,
    gv2forceInvites = WaveStore.internal.gv2ForceInvites,
    gv2ignoreP2PChanges = WaveStore.internal.gv2IgnoreP2PChanges,
    allowCensorshipSetting = WaveStore.internal.allowChangingCensorshipSetting,
    forceWebsocketMode = WaveStore.internal.isWebsocketModeForced,
    callingServer = WaveStore.internal.groupCallingServer,
    callingDataMode = WaveStore.internal.callingDataMode,
    callingDisableTelecom = WaveStore.internal.callingDisableTelecom,
    callingSetAudioConfig = WaveStore.internal.callingSetAudioConfig,
    callingUseOboeAdm = WaveStore.internal.callingUseOboeAdm,
    callingUseSoftwareAec = WaveStore.internal.callingUseSoftwareAec,
    callingUseSoftwareNs = WaveStore.internal.callingUseSoftwareNs,
    callingUseInputLowLatency = WaveStore.internal.callingUseInputLowLatency,
    callingUseInputVoiceComm = WaveStore.internal.callingUseInputVoiceComm,
    useBuiltInEmojiSet = WaveStore.internal.forceBuiltInEmoji,
    emojiVersion = null,
    removeSenderKeyMinimium = WaveStore.internal.removeSenderKeyMinimum,
    delayResends = WaveStore.internal.delayResends,
    disableStorageService = WaveStore.internal.storageServiceDisabled,
    canClearOnboardingState = WaveStore.story.hasDownloadedOnboardingStory && Stories.isFeatureEnabled(),
    pnpInitialized = WaveStore.misc.hasPniInitializedDevices,
    useConversationItemV2ForMedia = WaveStore.internal.useConversationItemV2Media,
    hasPendingOneTimeDonation = WaveStore.inAppPayments.getPendingOneTimeDonation() != null,
    hevcEncoding = WaveStore.internal.hevcEncoding,
    newCallingUi = WaveStore.internal.newCallingUi,
    callQualitySurveys = WaveStore.internal.callQualitySurveys,
    forceSplitPane = WaveStore.internal.forceSplitPane
  )

  fun onClearOnboardingState() {
    WaveStore.story.hasDownloadedOnboardingStory = false
    WaveStore.story.userHasViewedOnboardingStory = false
    Stories.onStorySettingsChanged(Recipient.self().id)
    refresh()
    StoryOnboardingDownloadJob.enqueueIfNeeded()
  }

  fun setUseNewCallingUi(newCallingUi: Boolean) {
    WaveStore.internal.newCallingUi = newCallingUi
    refresh()
  }

  fun setEnableCallQualitySurveys(enabled: Boolean) {
    WaveStore.internal.callQualitySurveys = enabled
    refresh()
  }

  fun setForceSplitPane(forceSplitPane: Boolean) {
    WaveStore.internal.forceSplitPane = forceSplitPane
    refresh()
  }

  class Factory(private val repository: InternalSettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(InternalSettingsViewModel(repository)))
    }
  }
}

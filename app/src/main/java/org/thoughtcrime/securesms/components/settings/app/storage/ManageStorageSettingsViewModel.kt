/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wave.core.util.concurrent.WaveExecutors
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.BackupUpgradeAvailabilityChecker
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.WaveDatabase.Companion.media
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.OptimizeMediaJob
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.RemoteConfig

class ManageStorageSettingsViewModel : ViewModel() {

  private val store = MutableStateFlow(
    ManageStorageState(
      keepMessagesDuration = WaveStore.settings.keepMessagesDuration,
      lengthLimit = if (WaveStore.settings.isTrimByLengthEnabled) WaveStore.settings.threadTrimLength else ManageStorageState.NO_LIMIT,
      syncTrimDeletes = WaveStore.settings.shouldSyncThreadTrimDeletes()
    )
  )
  val state = store.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      InAppPaymentsRepository.observeLatestBackupPayment()
        .collectLatest { payment ->
          store.update { it.copy(isPaidTierPending = payment.state == InAppPaymentTable.State.PENDING) }
        }
    }

    viewModelScope.launch {
      store.update {
        it.copy(onDeviceStorageOptimizationState = getOnDeviceStorageOptimizationState())
      }
    }
  }

  fun refresh() {
    viewModelScope.launch {
      val breakdown: MediaTable.StorageBreakdown = media.getStorageBreakdown()
      store.update { it.copy(breakdown = breakdown) }
    }
  }

  fun deleteChatHistory() {
    WaveExecutors.BOUNDED_IO.execute {
      WaveDatabase.threads.deleteAllConversations()
      AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
    }
  }

  fun setKeepMessagesDuration(newDuration: KeepMessagesDuration) {
    WaveStore.settings.setKeepMessagesForDuration(newDuration)
    AppDependencies.trimThreadsByDateManager.scheduleIfNecessary()

    store.update { it.copy(keepMessagesDuration = newDuration) }
  }

  fun showConfirmKeepDurationChange(newDuration: KeepMessagesDuration): Boolean {
    return newDuration.ordinal > state.value.keepMessagesDuration.ordinal
  }

  fun setChatLengthLimit(newLimit: Int) {
    val restrictingChange = isRestrictingLengthLimitChange(newLimit)

    WaveStore.settings.setThreadTrimByLengthEnabled(newLimit != ManageStorageState.NO_LIMIT)
    WaveStore.settings.threadTrimLength = newLimit
    store.update { it.copy(lengthLimit = newLimit) }

    if (WaveStore.settings.isTrimByLengthEnabled && restrictingChange) {
      WaveExecutors.BOUNDED.execute {
        val keepMessagesDuration = WaveStore.settings.keepMessagesDuration

        val trimBeforeDate = if (keepMessagesDuration != KeepMessagesDuration.FOREVER) {
          System.currentTimeMillis() - keepMessagesDuration.duration
        } else {
          ThreadTable.NO_TRIM_BEFORE_DATE_SET
        }

        WaveDatabase.threads.trimAllThreads(newLimit, trimBeforeDate)
      }
    }
  }

  fun showConfirmSetChatLengthLimit(newLimit: Int): Boolean {
    return isRestrictingLengthLimitChange(newLimit)
  }

  fun setSyncTrimDeletes(syncTrimDeletes: Boolean) {
    WaveStore.settings.setSyncThreadTrimDeletes(syncTrimDeletes)
    store.update { it.copy(syncTrimDeletes = syncTrimDeletes) }
  }

  fun setOptimizeStorage(enabled: Boolean) {
    viewModelScope.launch {
      val storageState = getOnDeviceStorageOptimizationState()
      if (storageState >= OnDeviceStorageOptimizationState.DISABLED) {
        WaveStore.backup.optimizeStorage = enabled
        store.update {
          it.copy(
            onDeviceStorageOptimizationState = if (enabled) OnDeviceStorageOptimizationState.ENABLED else OnDeviceStorageOptimizationState.DISABLED,
            storageOptimizationStateChanged = true
          )
        }
      }
    }
  }

  private fun isRestrictingLengthLimitChange(newLimit: Int): Boolean {
    return state.value.lengthLimit == ManageStorageState.NO_LIMIT || (newLimit != ManageStorageState.NO_LIMIT && newLimit < state.value.lengthLimit)
  }

  private suspend fun getOnDeviceStorageOptimizationState(): OnDeviceStorageOptimizationState {
    return when {
      !WaveStore.backup.areBackupsEnabled || !BackupUpgradeAvailabilityChecker.isUpgradeAvailable(AppDependencies.application) || (!RemoteConfig.internalUser && !Environment.IS_STAGING) -> OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE
      WaveStore.backup.backupTier != MessageBackupTier.PAID -> OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER
      WaveStore.backup.optimizeStorage -> OnDeviceStorageOptimizationState.ENABLED
      else -> OnDeviceStorageOptimizationState.DISABLED
    }
  }

  override fun onCleared() {
    if (state.value.storageOptimizationStateChanged) {
      when (state.value.onDeviceStorageOptimizationState) {
        OnDeviceStorageOptimizationState.DISABLED -> RestoreOptimizedMediaJob.enqueue()
        OnDeviceStorageOptimizationState.ENABLED -> OptimizeMediaJob.enqueue()
        else -> Unit
      }
    }
  }

  enum class OnDeviceStorageOptimizationState {
    /**
     * The entire feature is not available and the option should not be displayed to the user.
     */
    FEATURE_NOT_AVAILABLE,

    /**
     * The feature is available, but the user is not on the paid backups plan.
     */
    REQUIRES_PAID_TIER,

    /**
     * The user is on the paid backups plan but optimized storage is disabled.
     */
    DISABLED,

    /**
     * The user is on the paid backups plan and optimized storage is enabled.
     */
    ENABLED
  }

  @Immutable
  data class ManageStorageState(
    val keepMessagesDuration: KeepMessagesDuration,
    val lengthLimit: Int,
    val syncTrimDeletes: Boolean,
    val breakdown: MediaTable.StorageBreakdown? = null,
    val onDeviceStorageOptimizationState: OnDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE,
    val storageOptimizationStateChanged: Boolean = false,
    val isPaidTierPending: Boolean = false
  ) {
    companion object {
      const val NO_LIMIT = 0
    }
  }
}

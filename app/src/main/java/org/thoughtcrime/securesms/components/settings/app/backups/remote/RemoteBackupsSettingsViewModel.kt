/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.wave.core.util.bytes
import org.wave.core.util.concurrent.WaveDispatchers
import org.wave.core.util.logging.Log
import org.wave.core.util.mebiBytes
import org.wave.core.util.throttleLatest
import org.wave.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.BackupUpgradeAvailabilityChecker
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.backups.BackupState
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateObserver
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.attachmentUpdates
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraint
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.waveservice.api.NetworkResult
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteBackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteBackupsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    RemoteBackupsSettingsState(
      tier = WaveStore.backup.backupTier,
      backupState = BackupStateObserver.getNonIOBackupState(),
      backupsEnabled = WaveStore.backup.areBackupsEnabled,
      canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
      canViewBackupKey = !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      lastBackupTimestamp = WaveStore.backup.lastBackupTime,
      canBackUpUsingCellular = WaveStore.backup.backupWithCellular,
      canRestoreUsingCellular = WaveStore.backup.restoreWithCellular,
      includeDebuglog = WaveStore.internal.includeDebuglogInBackup.takeIf { RemoteConfig.internalUser },
      backupCreationError = WaveStore.backup.backupCreationError,
      lastMessageCutoffTime = WaveStore.backup.lastUsedMessageCutoffTime
    )
  )

  private val _restoreState: MutableStateFlow<BackupRestoreState> = MutableStateFlow(BackupRestoreState.None)
  private val latestPurchaseId = MutableSharedFlow<InAppPaymentTable.InAppPaymentId>()

  val state: StateFlow<RemoteBackupsSettingsState> = _state
  val restoreState: StateFlow<BackupRestoreState> = _restoreState

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val isBillingApiAvailable = AppDependencies.billingApi.getApiAvailability().isSuccess
      if (isBillingApiAvailable) {
        _state.update {
          it.copy(isPaidTierPricingAvailable = true)
        }
      } else {
        val paidType = BackupRepository.getPaidType()
        _state.update {
          it.copy(isPaidTierPricingAvailable = paidType is NetworkResult.Success)
        }
      }
    }

    viewModelScope.launch {
      _state.update {
        it.copy(isGooglePlayServicesAvailable = BackupUpgradeAvailabilityChecker.isUpgradeAvailable(AppDependencies.application))
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      refreshBackupMediaSizeState()
    }

    viewModelScope.launch(Dispatchers.IO) {
      WaveStore.backup.deletionStateFlow.collectLatest {
        refresh()
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      latestPurchaseId
        .flatMapLatest { id -> InAppPaymentsRepository.observeUpdates(id).asFlow() }
        .collectLatest { purchase ->
          Log.d(TAG, "Refreshing state after archive IAP update.")
          refreshState(purchase)
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      AppDependencies
        .databaseObserver
        .attachmentUpdates()
        .throttleLatest(5.seconds)
        .collectLatest {
          refreshBackupMediaSizeState()
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      var optimizedRemainingBytes = 0L
      while (isActive) {
        if (ArchiveRestoreProgress.state.let { it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED }) {
          Log.d(TAG, "Backup is being restored. Collecting updates.")
          ArchiveRestoreProgress
            .stateFlow
            .takeWhile { it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED }
            .onEach { latest -> _restoreState.update { BackupRestoreState.Restoring(latest) } }
            .collect()
        } else if (
          !WaveStore.backup.optimizeStorage &&
          WaveStore.backup.userManuallySkippedMediaRestore &&
          WaveDatabase.attachments.getOptimizedMediaAttachmentSize().also { optimizedRemainingBytes = it } > 0
        ) {
          _restoreState.update { BackupRestoreState.Ready(optimizedRemainingBytes.bytes.toUnitString()) }
        } else if (WaveStore.backup.totalRestorableAttachmentSize > 0L) {
          _restoreState.update { BackupRestoreState.Ready(WaveStore.backup.totalRestorableAttachmentSize.bytes.toUnitString()) }
        } else {
          _restoreState.update { BackupRestoreState.None }
        }

        delay(1.seconds)
      }
    }

    viewModelScope.launch {
      var previous: ArchiveUploadProgressState.State? = null
      ArchiveUploadProgress.progress
        .collect { current ->
          if (previous != null && previous != current.state && current.state == ArchiveUploadProgressState.State.None) {
            Log.d(TAG, "Refreshing state after archive upload.")
            refreshState(null)
          }
          previous = current.state
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      BackupStateObserver(viewModelScope).backupState.collect { state ->
        _state.update {
          it.copy(backupState = state)
        }
        refreshState(null)
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      BackupRepository.maybeFixAnyDanglingUploadProgress()
    }
  }

  fun setCanBackUpUsingCellular(canBackUpUsingCellular: Boolean) {
    WaveStore.backup.backupWithCellular = canBackUpUsingCellular
    _state.update {
      it.copy(
        canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
        canBackUpUsingCellular = canBackUpUsingCellular
      )
    }
  }

  fun setCanRestoreUsingCellular() {
    WaveStore.backup.restoreWithCellular = true
    _state.update { it.copy(canRestoreUsingCellular = true) }
  }

  fun beginMediaRestore() {
    BackupRepository.resumeMediaRestore()
  }

  fun cancelMediaRestore() {
    if (ArchiveRestoreProgress.state.restoreStatus == RestoreStatus.FINISHED) {
      ArchiveRestoreProgress.clearFinishedStatus()
    } else {
      requestDialog(RemoteBackupsSettingsState.Dialog.CANCEL_MEDIA_RESTORE_PROTECTION)
    }
  }

  fun skipMediaRestore() {
    BackupRepository.skipMediaRestore()

    if (WaveStore.backup.deletionState == DeletionState.AWAITING_MEDIA_DOWNLOAD) {
      BackupRepository.continueTurningOffAndDisablingBackups()
    }
  }

  fun requestDialog(dialog: RemoteBackupsSettingsState.Dialog) {
    _state.update { it.copy(dialog = dialog) }
  }

  fun requestSnackbar(snackbar: RemoteBackupsSettingsState.Snackbar) {
    _state.update { it.copy(snackbar = snackbar) }
  }

  fun getKeyRotationLimit() {
    viewModelScope.launch(WaveDispatchers.IO) {
      val result = BackupRepository.getKeyRotationLimit()
      val canRotateKey = if (result is NetworkResult.Success) {
        result.result.hasPermitsRemaining!!
      } else {
        Log.w(TAG, "Error while getting rotation limit: $result. Default to allowing key rotations.")
        true
      }

      if (!canRotateKey) {
        requestDialog(RemoteBackupsSettingsState.Dialog.KEY_ROTATION_LIMIT_REACHED)
      }
    }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val id = WaveDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)?.id

      if (id != null) {
        latestPurchaseId.emit(id)
      } else {
        refreshState(null)
      }
    }
  }

  fun turnOffAndDeleteBackups() {
    viewModelScope.launch {
      requestDialog(RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER)

      withContext(Dispatchers.IO) {
        BackupRepository.turnOffAndDisableBackups()
      }

      requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
    }
  }

  fun onBackupNowClick() {
    BackupMessagesJob.enqueue()
  }

  fun cancelUpload() {
    ArchiveUploadProgress.cancel()
  }

  fun setIncludeDebuglog(includeDebuglog: Boolean) {
    WaveStore.internal.includeDebuglogInBackup = includeDebuglog
    _state.update { it.copy(includeDebuglog = includeDebuglog) }
  }

  private fun refreshBackupMediaSizeState() {
    _state.update {
      val (mediaSize, mediaRetentionDays) = getBackupSize(it.tier, (it.backupState as? BackupState.WithTypeAndRenewalTime)?.messageBackupsType)
      it.copy(
        backupMediaSize = mediaSize,
        freeTierMediaRetentionDays = mediaRetentionDays,
        backupMediaDetails = if (RemoteConfig.internalUser || Environment.IS_STAGING) {
          RemoteBackupsSettingsState.BackupMediaDetails(
            awaitingRestore = WaveDatabase.attachments.getRemainingRestorableAttachmentSize().bytes,
            offloaded = WaveDatabase.attachments.getOptimizedMediaAttachmentSize().bytes,
            protoFileSize = WaveStore.backup.lastBackupProtoSize.bytes
          )
        } else null
      )
    }
  }

  private suspend fun refreshState(lastPurchase: InAppPaymentTable.InAppPayment?) {
    try {
      Log.i(TAG, "Performing a state refresh.")
      performStateRefresh(lastPurchase)
    } catch (e: Exception) {
      Log.w(TAG, "State refresh failed", e)
      throw e
    }
  }

  private suspend fun performStateRefresh(lastPurchase: InAppPaymentTable.InAppPayment?) {
    if (BackupRepository.shouldDisplayOutOfRemoteStorageSpaceUx()) {
      val paidType = BackupRepository.getPaidType()

      if (paidType is NetworkResult.Success) {
        val remoteStorageAllowance = paidType.result.storageAllowanceBytes.bytes
        val estimatedSize = getBackupSize(paidType.result.tier, paidType.result).first.bytes

        if (estimatedSize + 300.mebiBytes <= remoteStorageAllowance) {
          BackupRepository.clearOutOfRemoteStorageSpaceError()
        }

        _state.update {
          it.copy(
            totalAllowedStorageSpace = estimatedSize.toUnitString()
          )
        }
      } else {
        Log.w(TAG, "Failed to load PAID type.", paidType.getCause())
      }
    }

    val (mediaSize, mediaRetentionDays) = getBackupSize(_state.value.tier, (_state.value.backupState as? BackupState.WithTypeAndRenewalTime)?.messageBackupsType)

    _state.update {
      it.copy(
        tier = WaveStore.backup.backupTier,
        backupsEnabled = WaveStore.backup.areBackupsEnabled,
        lastBackupTimestamp = WaveStore.backup.lastBackupTime,
        canBackupMessagesJobRun = BackupMessagesConstraint.isMet(AppDependencies.application),
        backupMediaSize = mediaSize,
        freeTierMediaRetentionDays = mediaRetentionDays,
        canBackUpUsingCellular = WaveStore.backup.backupWithCellular,
        canRestoreUsingCellular = WaveStore.backup.restoreWithCellular,
        isOutOfStorageSpace = BackupRepository.shouldDisplayOutOfRemoteStorageSpaceUx(),
        hasRedemptionError = lastPurchase?.data?.error?.data_ == "409",
        backupCreationError = WaveStore.backup.backupCreationError,
        lastMessageCutoffTime = WaveStore.backup.lastUsedMessageCutoffTime
      )
    }
  }

  private fun getBackupSize(tier: MessageBackupTier?, messageBackupsType: MessageBackupsType?): Pair<Long, Int> {
    if (tier == null) {
      return -1L to 0
    }

    val mediaRetentionDays = if (messageBackupsType is MessageBackupsType.Free) {
      messageBackupsType.mediaRetentionDays
    } else {
      when (tier) {
        MessageBackupTier.FREE -> {
          when (val result = BackupRepository.getFreeType()) {
            is NetworkResult.Success -> result.result.mediaRetentionDays
            else -> RemoteConfig.messageQueueTime.milliseconds.inWholeDays.toInt()
          }
        }

        MessageBackupTier.PAID -> 0
      }
    }

    return if (WaveStore.backup.hasBackupBeenUploaded || WaveStore.backup.lastBackupTime > 0L) {
      when (tier) {
        MessageBackupTier.PAID -> (WaveStore.backup.lastBackupProtoSize + WaveDatabase.attachments.getPaidEstimatedArchiveMediaSize()) to -1
        MessageBackupTier.FREE -> {
          if (mediaRetentionDays > 0) {
            (WaveStore.backup.lastBackupProtoSize + WaveDatabase.attachments.getFreeEstimatedArchiveMediaSize(System.currentTimeMillis() - mediaRetentionDays.days.inWholeMilliseconds)) to mediaRetentionDays
          } else {
            -1L to -1
          }
        }
      }
    } else {
      0L to mediaRetentionDays
    }
  }
}

/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.keyvalue.includeDeviceToDeviceTransfer
import org.thoughtcrime.securesms.keyvalue.skippedRestoreChoice
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registration.ui.restore.RestoreMethod
import org.thoughtcrime.securesms.registration.ui.restore.StorageServiceRestore
import org.whispersystems.waveservice.api.provisioning.RestoreMethod as ApiRestoreMethod

/**
 * Shared view model for the restore flow.
 */
class RestoreViewModel : ViewModel() {
  private val store = MutableStateFlow(RestoreState())
  val uiState = store.asLiveData()

  var showStorageAccountRestoreProgress by mutableStateOf(false)
    private set

  fun setNextIntent(nextIntent: Intent) {
    store.update {
      it.copy(nextIntent = nextIntent)
    }
  }

  fun setBackupFileUri(backupFileUri: Uri) {
    store.update {
      it.copy(backupFile = backupFileUri)
    }
  }

  fun getBackupFileUri(): Uri? = store.value.backupFile

  fun getNextIntent(): Intent? = store.value.nextIntent

  fun hasNoRestoreMethods(): Boolean {
    return getAvailableRestoreMethods().isEmpty()
  }

  fun getAvailableRestoreMethods(): List<RestoreMethod> {
    if (WaveStore.registration.isOtherDeviceAndroid || WaveStore.registration.restoreDecisionState.skippedRestoreChoice) {
      val methods = mutableListOf(RestoreMethod.FROM_LOCAL_BACKUP_V1)

      if (WaveStore.registration.isOtherDeviceAndroid && WaveStore.registration.restoreDecisionState.includeDeviceToDeviceTransfer) {
        methods.add(0, RestoreMethod.FROM_OLD_DEVICE)
      }

      when (WaveStore.backup.backupTier) {
        MessageBackupTier.FREE -> methods.add(1, RestoreMethod.FROM_SIGNAL_BACKUPS)
        MessageBackupTier.PAID -> methods.add(0, RestoreMethod.FROM_SIGNAL_BACKUPS)
        null -> if (!WaveStore.backup.restoringViaQr) {
          methods.add(1, RestoreMethod.FROM_SIGNAL_BACKUPS)
        }
      }

      return methods
    }

    if (WaveStore.backup.restoringViaQr && WaveStore.backup.backupTier != null) {
      return listOf(RestoreMethod.FROM_SIGNAL_BACKUPS)
    }

    return emptyList()
  }

  fun hasRestoredAccountEntropyPool(): Boolean {
    return WaveStore.account.restoredAccountEntropyPool
  }

  fun hasRestoredBackupDataFromQr(): Boolean {
    return WaveStore.backup.restoringViaQr && WaveStore.backup.backupTier != null
  }

  fun skipRestore() {
    WaveStore.registration.restoreDecisionState = RestoreDecisionState.Skipped

    viewModelScope.launch {
      QuickRegistrationRepository.setRestoreMethodForOldDevice(ApiRestoreMethod.DECLINE)
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (hasRestoredAccountEntropyPool() || WaveStore.svr.masterKeyForInitialDataRestore != null) {
      showStorageAccountRestoreProgress = true
      StorageServiceRestore.restore()
    }
  }
}

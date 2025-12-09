/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wave.core.models.ServiceId.ACI
import org.wave.core.models.backup.MessageBackupKey
import org.wave.core.util.Hex
import org.wave.core.util.ThreadUtil
import org.wave.core.util.bytes
import org.wave.core.util.concurrent.WaveExecutors
import org.wave.core.util.copyTo
import org.wave.core.util.logging.Log
import org.wave.core.util.readNBytesOrThrow
import org.wave.core.util.roundedString
import org.wave.core.util.stream.LimitedInputStream
import org.wave.libwave.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveValidator
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.DebugBackupMetadata
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RemoteRestoreResult
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.ArchiveResult
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver.FailureCause
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader.Companion.MAC_SIZE
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.DebugAttachmentStats
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.RestoreLocalAttachmentJob
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.net.WaveNetwork
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.svr.SvrBApi
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class InternalBackupPlaygroundViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(InternalBackupPlaygroundViewModel::class)
  }

  val disposables = CompositeDisposable()

  private val _state: MutableState<ScreenState> = mutableStateOf(
    ScreenState(
      canReadWriteBackupDirectory = WaveStore.settings.waveBackupDirectory?.let {
        val file = DocumentFile.fromTreeUri(AppDependencies.application, it)
        file != null && file.canWrite() && file.canRead()
      } ?: false,
      backupTier = WaveStore.backup.backupTier
    )
  )
  val state: State<ScreenState> = _state

  val statsState: MutableStateFlow<StatsState> = MutableStateFlow(StatsState())

  enum class DialogState {
    None,
    ImportCredentials
  }

  fun exportEncrypted(openStream: () -> OutputStream, appendStream: () -> OutputStream) {
    _state.value = _state.value.copy(statusMessage = "Exporting encrypted backup to disk...")
    disposables += Single
      .fromCallable {
        BackupRepository.exportForDebugging(
          outputStream = openStream(),
          append = { bytes -> appendStream().use { it.write(bytes) } }
        )
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        _state.value = _state.value.copy(statusMessage = "Encrypted backup complete!")
      }
  }

  fun exportPlaintext(openStream: () -> OutputStream, appendStream: () -> OutputStream) {
    _state.value = _state.value.copy(statusMessage = "Exporting plaintext backup to disk...")
    disposables += Single
      .fromCallable {
        BackupRepository.exportForDebugging(
          outputStream = openStream(),
          append = { bytes -> appendStream().use { it.write(bytes) } },
          plaintext = true
        )
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        _state.value = _state.value.copy(statusMessage = "Plaintext backup complete!")
      }
  }

  fun validateBackup() {
    _state.value = _state.value.copy(statusMessage = "Exporting to a temporary file...")
    val tempFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

    disposables += Single
      .fromCallable {
        BackupRepository.exportForDebugging(
          outputStream = FileOutputStream(tempFile),
          append = { bytes -> tempFile.appendBytes(bytes) }
        )
        _state.value = _state.value.copy(statusMessage = "Export complete! Validating...")
        ArchiveValidator.validateLocalOrLinking(tempFile, WaveStore.backup.messageBackupKey, forTransfer = false)
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { result ->
        val message = when (result) {
          is ArchiveValidator.ValidationResult.ReadError -> "Failed to read backup file!"
          ArchiveValidator.ValidationResult.Success -> "Validation passed!"
          is ArchiveValidator.ValidationResult.MessageValidationError -> {
            Log.w(TAG, "Validation failed! Details: ${result.messageDetails}", result.exception)
            "Validation failed :( Check the logs for details."
          }

          is ArchiveValidator.ValidationResult.RecipientDuplicateE164Error -> {
            Log.w(TAG, "Validation failed with a duplicate recipient! Details: ${result.details}", result.exception)
            "Validation failed :( Check the logs for details."
          }
        }
        _state.value = _state.value.copy(statusMessage = message)
      }
  }

  fun triggerBackupJob() {
    _state.value = _state.value.copy(statusMessage = "Upload job in progress...")

    disposables += Single.fromCallable { AppDependencies.jobManager.runSynchronously(BackupMessagesJob(), 120_000) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { result ->
        _state.value = _state.value.copy(statusMessage = "Upload job complete! Result: ${result.takeIf { it.isPresent }?.get() ?: "N/A"}")
      }
  }

  fun importEncryptedBackup(length: Long, inputStreamFactory: () -> InputStream) {
    val customCredentials: ImportCredentials? = _state.value.customBackupCredentials
    _state.value = _state.value.copy(statusMessage = "Importing encrypted backup...", customBackupCredentials = null)

    val self = Recipient.self()
    val aci = customCredentials?.aci ?: self.aci.get()
    val selfData = BackupRepository.SelfData(aci, self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))
    val backupKey = customCredentials?.messageBackupKey ?: WaveStore.backup.messageBackupKey

    disposables += Single.fromCallable { BackupRepository.importForDebugging(length, inputStreamFactory, selfData, backupKey) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(statusMessage = "Encrypted backup import complete!")
      }
  }

  fun import(uri: Uri) {
    _state.value = _state.value.copy(statusMessage = "Importing new-style local backup...")

    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    disposables += Single.fromCallable {
      val archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, uri)!!
      val snapshotInfo = archiveFileSystem.listSnapshots().firstOrNull() ?: return@fromCallable ArchiveResult.failure(FailureCause.MAIN_STREAM)
      val snapshotFileSystem = SnapshotFileSystem(AppDependencies.application, snapshotInfo.file)

      LocalArchiver.import(snapshotFileSystem, selfData)

      val mediaNameToFileInfo = archiveFileSystem.filesFileSystem.allFiles()
      RestoreLocalAttachmentJob.enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(statusMessage = "New-style local backup import complete!")
      }
  }

  fun haltAllJobs() {
    ArchiveUploadProgress.cancel()

    AppDependencies.jobManager.cancelAllInQueue("BackupRestoreJob")
    AppDependencies.jobManager.cancelAllInQueue("__LOCAL_BACKUP__")
  }

  fun fetchRemoteBackupAndWritePlaintext(outputStream: OutputStream?) {
    check(outputStream != null)

    WaveExecutors.BOUNDED_IO.execute {
      Log.d(TAG, "Downloading file...")
      val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

      when (val result = BackupRepository.downloadBackupFile(tempBackupFile)) {
        is NetworkResult.Success -> Log.i(TAG, "Download successful")
        else -> {
          Log.w(TAG, "Failed to download backup file", result.getCause())
          throw IOException(result.getCause())
        }
      }

      val forwardSecrecyMetadata = tempBackupFile.inputStream().use { EncryptedBackupReader.readForwardSecrecyMetadata(it) }
      if (forwardSecrecyMetadata == null) {
        throw IOException("Failed to read forward secrecy metadata!")
      }

      val svrBAuth = when (val result = BackupRepository.getSvrBAuth()) {
        is NetworkResult.Success -> result.result
        else -> throw IOException("Failed to read forward secrecy metadata!")
      }

      val forwardSecrecyToken = when (val result = WaveNetwork.svrB.restore(svrBAuth, WaveStore.backup.messageBackupKey, forwardSecrecyMetadata)) {
        is SvrBApi.RestoreResult.Success -> result.data.forwardSecrecyToken
        else -> throw IOException("Failed to read forward secrecy metadata! $result")
      }

      val encryptedStream = tempBackupFile.inputStream().apply {
        EncryptedBackupReader.readForwardSecrecyMetadata(this)
      }
      val iv = encryptedStream.readNBytesOrThrow(16)
      val backupKey = WaveStore.backup.messageBackupKey
      val keyMaterial = backupKey.deriveBackupSecrets(Recipient.self().aci.get(), forwardSecrecyToken)
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(keyMaterial.aesKey, "AES"), IvParameterSpec(iv))
      }

      val plaintextStream = GZIPInputStream(
        CipherInputStream(
          LimitedInputStream(
            wrapped = encryptedStream,
            maxBytes = tempBackupFile.length() - MAC_SIZE - tempBackupFile.inputStream().use { EncryptedBackupReader.getForwardSecrecyPrefixDataLength(it) }
          ),
          cipher
        )
      )

      Log.d(TAG, "Copying...")
      plaintextStream.copyTo(outputStream)
      Log.d(TAG, "Done!")
    }
  }

  fun checkRemoteBackupState() {
    disposables += Single
      .fromCallable {
        BackupRepository.restoreBackupFileTimestamp()
        BackupRepository.debugGetRemoteBackupState()
      }
      .subscribeOn(Schedulers.io())
      .subscribe { result ->
        when {
          result is NetworkResult.Success -> {
            _state.value = _state.value.copy(
              statusMessage = "Remote backup exists. ${result.result.mediaCount} media items, using ${result.result.usedSpace} bytes (${result.result.usedSpace.bytes.inMebiBytes.roundedString(3)} MiB)"
            )
          }

          result is NetworkResult.StatusCodeError && result.code == 404 -> {
            _state.value = _state.value.copy(statusMessage = "Remote backup does not exists.")
          }

          else -> {
            Log.w(TAG, "Error checking remote backup state", result.getCause())
            _state.value = _state.value.copy(statusMessage = "Failed to fetch remote backup state.")
          }
        }
      }
  }

  fun wipeAllDataAndRestoreFromRemote(afterDbRestoreCallback: () -> Unit) {
    WaveExecutors.BOUNDED_IO.execute {
      WaveStore.backup.restoreWithCellular = false
      restoreFromRemote(afterDbRestoreCallback)
    }
  }

  fun onImportSelected() {
    _state.value = _state.value.copy(dialog = DialogState.ImportCredentials)
  }

  /** True if data is valid, else false */
  fun onImportConfirmed(aci: String, backupKey: String): Boolean {
    val parsedAci: ACI? = ACI.parseOrNull(aci)

    if (aci.isNotBlank() && parsedAci == null) {
      _state.value = _state.value.copy(statusMessage = "Invalid ACI! Cannot import.")
      return false
    }

    val parsedBackupKey: MessageBackupKey? = try {
      val bytes = Hex.fromStringOrThrow(backupKey)
      MessageBackupKey(bytes)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse key!", e)
      null
    }

    if (backupKey.isNotBlank() && parsedBackupKey == null) {
      _state.value = _state.value.copy(statusMessage = "Invalid AEP! Cannot import.")
      return false
    }

    _state.value = state.value.copy(
      customBackupCredentials = ImportCredentials(
        messageBackupKey = parsedBackupKey ?: WaveStore.backup.messageBackupKey,
        aci = parsedAci ?: WaveStore.account.aci!!
      )
    )

    return true
  }

  fun onDialogDismissed() {
    _state.value = _state.value.copy(dialog = DialogState.None)
  }

  private fun restoreFromRemote(afterDbRestoreCallback: () -> Unit) {
    _state.value = _state.value.copy(statusMessage = "Importing from remote...")

    viewModelScope.launch {
      when (val result = BackupRepository.restoreRemoteBackup()) {
        RemoteRestoreResult.Success -> {
          _state.value = _state.value.copy(statusMessage = "Import complete!")
          ThreadUtil.runOnMain { afterDbRestoreCallback() }
        }
        RemoteRestoreResult.Canceled,
        RemoteRestoreResult.Failure,
        RemoteRestoreResult.PermanentSvrBFailure,
        RemoteRestoreResult.NetworkError -> {
          _state.value = _state.value.copy(statusMessage = "Import failed! $result")
        }
      }
    }
  }

  fun loadStats() {
    viewModelScope.launch(Dispatchers.IO) {
      launch {
        var stats = WaveDatabase.attachments.debugGetAttachmentStats()

        statsState.update { it.copy(attachmentStats = stats) }
      }
    }
  }

  fun loadRemoteStats() {
    viewModelScope.launch(Dispatchers.IO) {
      launch {
        statsState.update { it.copy(loadingRemoteStats = true) }
        val (remoteState: DebugBackupMetadata?, errorMsg: String?) = when (val result = BackupRepository.debugGetRemoteBackupState()) {
          is NetworkResult.Success -> result.result to null
          else -> null to result.toString()
        }
        statsState.update { it.copy(remoteState = remoteState, remoteFailureMsg = errorMsg, loadingRemoteStats = false) }
      }
    }
  }

  suspend fun deleteRemoteBackupData(): Boolean = withContext(Dispatchers.IO) {
    when (val result = BackupRepository.debugDeleteAllArchivedMedia()) {
      is NetworkResult.Success -> Log.i(TAG, "Remote data deleted")
      else -> {
        Log.w(TAG, "Unable to delete media", result.getCause())
        return@withContext false
      }
    }

    when (val result = BackupRepository.deleteBackup()) {
      is NetworkResult.Success -> {
        WaveStore.backup.backupsInitialized = false
        WaveStore.backup.messageCredentials.clearAll()
        WaveStore.backup.mediaCredentials.clearAll()
        WaveStore.backup.cachedMediaCdnPath = null
        return@withContext true
      }
      else -> Log.w(TAG, "Unable to delete remote data", result.getCause())
    }

    return@withContext false
  }

  suspend fun clearLocalMediaBackupState() = withContext(Dispatchers.IO) {
    WaveDatabase.attachments.clearAllArchiveData()
  }

  override fun onCleared() {
    disposables.clear()
  }

  data class ScreenState(
    val canReadWriteBackupDirectory: Boolean = false,
    val backupTier: MessageBackupTier? = null,
    val statusMessage: String? = null,
    val customBackupCredentials: ImportCredentials? = null,
    val dialog: DialogState = DialogState.None
  )

  sealed class RemoteBackupState {
    data object Unknown : RemoteBackupState()
    data object NotFound : RemoteBackupState()
    data class Available(val response: DebugBackupMetadata) : RemoteBackupState()
  }

  data class MediaState(
    val attachments: List<BackupAttachment> = emptyList(),
    val inProgressMediaIds: Set<AttachmentId> = emptySet(),
    val error: MediaStateError? = null
  ) {
    fun update(
      attachments: List<BackupAttachment> = this.attachments,
      inProgress: Set<AttachmentId> = this.inProgressMediaIds
    ): MediaState {
      val updatedAttachments = attachments.map {
        val state = if (inProgress.contains(it.dbAttachment.attachmentId)) {
          BackupAttachment.State.IN_PROGRESS
        } else if (it.dbAttachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED) {
          if (it.dbAttachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
            BackupAttachment.State.UPLOADED_FINAL
          } else {
            BackupAttachment.State.UPLOADED_UNDOWNLOADED
          }
        } else if (it.dbAttachment.remoteLocation != null) {
          BackupAttachment.State.ATTACHMENT_CDN
        } else {
          BackupAttachment.State.LOCAL_ONLY
        }

        it.copy(state = state)
      }

      return copy(
        attachments = updatedAttachments
      )
    }
  }

  data class BackupAttachment(
    val dbAttachment: DatabaseAttachment,
    val state: State = State.LOCAL_ONLY
  ) {
    val id: AttachmentId = dbAttachment.attachmentId
    val title: String = dbAttachment.attachmentId.toString()

    enum class State {
      ATTACHMENT_CDN,
      LOCAL_ONLY,
      UPLOADED_UNDOWNLOADED,
      UPLOADED_FINAL,
      IN_PROGRESS
    }
  }

  data class MediaStateError(
    val id: UUID = UUID.randomUUID(),
    val errorText: String
  )

  fun <T> MutableState<T>.set(update: T.() -> T) {
    this.value = this.value.update()
  }

  data class ImportCredentials(
    val messageBackupKey: MessageBackupKey,
    val aci: ACI
  )

  data class StatsState(
    val attachmentStats: DebugAttachmentStats? = null,
    val loadingRemoteStats: Boolean = false,
    val remoteState: DebugBackupMetadata? = null,
    val remoteFailureMsg: String? = null
  ) {
    val valid = attachmentStats != null
  }
}

/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import org.wave.core.util.logging.Log
import org.wave.libwave.protocol.InvalidMessageException
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.InvalidAttachmentException
import org.thoughtcrime.securesms.backup.v2.ArchiveDatabaseExecutor
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.createArchiveThumbnailPointer
import org.thoughtcrime.securesms.backup.v2.requireThumbnailMediaName
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobLogger.format
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.waveservice.api.messages.AttachmentTransferProgress
import org.whispersystems.waveservice.api.messages.WaveServiceAttachment
import org.whispersystems.waveservice.api.push.exceptions.MissingConfigurationException
import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Download attachment from locations as specified in their record.
 */
class RestoreAttachmentThumbnailJob private constructor(
  parameters: Parameters,
  private val messageId: Long,
  val attachmentId: AttachmentId
) : BaseJob(parameters) {

  companion object {
    const val KEY = "RestoreAttachmentThumbnailJob"
    val TAG = Log.tag(RestoreAttachmentThumbnailJob::class.java)

    private const val KEY_MESSAGE_ID = "message_id"
    private const val KEY_ATTACHMENT_ID = "part_row_id"

    @JvmStatic
    fun constructQueueString(attachmentId: AttachmentId): String {
      // TODO: decide how many queues
      return "RestoreAttachmentThumbnailJob"
    }
  }

  constructor(messageId: Long, attachmentId: AttachmentId, highPriority: Boolean = false) : this(
    Parameters.Builder()
      .setQueue(constructQueueString(attachmentId))
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setGlobalPriority(if (highPriority) Parameters.PRIORITY_HIGH else Parameters.PRIORITY_DEFAULT)
      .build(),
    messageId,
    attachmentId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_MESSAGE_ID, messageId)
      .putLong(KEY_ATTACHMENT_ID, attachmentId.id)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    WaveDatabase.attachments.setThumbnailRestoreState(attachmentId, AttachmentTable.ThumbnailRestoreState.IN_PROGRESS)
  }

  @Throws(Exception::class, IOException::class, InvalidAttachmentException::class, InvalidMessageException::class, MissingConfigurationException::class)
  public override fun onRun() {
    try {
      doWork()
    } catch (e: IOException) {
      BackupRepository.checkForOutOfStorageError(TAG)
      throw e
    }
  }

  private fun doWork() {
    Log.i(TAG, "onRun() messageId: $messageId  attachmentId: $attachmentId")

    val attachment = WaveDatabase.attachments.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.")
      return
    }

    if (attachment.thumbnailRestoreState == AttachmentTable.ThumbnailRestoreState.FINISHED) {
      Log.w(TAG, "$attachmentId already has thumbnail downloaded")
      return
    }

    if (attachment.thumbnailRestoreState == AttachmentTable.ThumbnailRestoreState.NONE) {
      Log.w(TAG, "$attachmentId has no thumbnail state")
      return
    }

    if (attachment.thumbnailRestoreState == AttachmentTable.ThumbnailRestoreState.PERMANENT_FAILURE) {
      Log.w(TAG, "$attachmentId thumbnail permanently failed")
      return
    }

    if (attachment.dataHash == null) {
      Log.w(TAG, "$attachmentId has no plaintext hash! Cannot proceed.")
      return
    }

    val maxThumbnailSize: Long = RemoteConfig.maxAttachmentReceiveSizeBytes
    val thumbnailTransferFile: File = WaveDatabase.attachments.createArchiveThumbnailTransferFile()

    val progressListener = object : WaveServiceAttachment.ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) = Unit
      override fun shouldCancel(): Boolean = this@RestoreAttachmentThumbnailJob.isCanceled
    }

    val cdnCredentials = BackupRepository.getCdnReadCredentials(BackupRepository.CredentialType.MEDIA, attachment.archiveCdn ?: RemoteConfig.backupFallbackArchiveCdn).successOrThrow().headers
    val pointer = attachment.createArchiveThumbnailPointer()

    Log.i(TAG, "Downloading thumbnail for $attachmentId")
    val decryptingStream = AppDependencies.waveServiceMessageReceiver
      .retrieveArchivedThumbnail(
        WaveStore.backup.mediaRootBackupKey.deriveMediaSecrets(attachment.requireThumbnailMediaName()),
        cdnCredentials,
        thumbnailTransferFile,
        pointer,
        maxThumbnailSize,
        progressListener
      )

    decryptingStream.use { input ->
      ArchiveDatabaseExecutor.runBlocking {
        WaveDatabase.attachments.finalizeAttachmentThumbnailAfterDownload(attachmentId, attachment.dataHash, attachment.remoteKey, input, thumbnailTransferFile)
      }
    }

    if (!WaveDatabase.messages.isStory(messageId)) {
      AppDependencies.messageNotifier.updateNotification(context)
    }
  }

  override fun onFailure() {
    Log.w(TAG, format(this, "onFailure() thumbnail messageId: $messageId  attachmentId: $attachmentId "))

    ArchiveDatabaseExecutor.runBlocking {
      WaveDatabase.attachments.setThumbnailRestoreProgressFailed(attachmentId, messageId)
    }
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    if (exception is NonSuccessfulResponseCodeException) {
      if (exception.code == 404) {
        Log.w(TAG, "[$attachmentId-thumbnail] Unable to find file!")
        return false
      }
      if (exception.code == 403) {
        Log.w(TAG, "[$attachmentId-thumbnail] No permission!")
        return false
      }
    }
    return exception is IOException
  }

  class Factory : Job.Factory<RestoreAttachmentThumbnailJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RestoreAttachmentThumbnailJob {
      val data = JsonJobData.deserialize(serializedData)
      return RestoreAttachmentThumbnailJob(
        parameters = parameters,
        messageId = data.getLong(KEY_MESSAGE_ID),
        attachmentId = AttachmentId(data.getLong(KEY_ATTACHMENT_ID))
      )
    }
  }
}

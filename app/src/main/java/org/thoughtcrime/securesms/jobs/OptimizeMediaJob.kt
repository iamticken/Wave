/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.wave.core.util.DiskUtil
import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.WaveStore
import kotlin.time.Duration.Companion.days

/**
 * Optimizes media storage by relying on backups for full copies of files and only keeping thumbnails locally.
 */
class OptimizeMediaJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(OptimizeMediaJob::class)
    const val KEY = "OptimizeMediaJob"

    fun enqueue() {
      if (!WaveStore.backup.optimizeStorage || !WaveStore.backup.backsUpMedia) {
        Log.i(TAG, "Optimize media is not enabled, skipping. backsUpMedia: ${WaveStore.backup.backsUpMedia} optimizeStorage: ${WaveStore.backup.optimizeStorage}")
        return
      }

      AppDependencies.jobManager.add(OptimizeMediaJob())
    }
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setQueue("OptimizeMediaJob")
      .setMaxInstancesForQueue(2)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(3)
      .build()
  )

  override fun run(): Result {
    if (!WaveStore.backup.optimizeStorage || !WaveStore.backup.backsUpMedia) {
      Log.i(TAG, "Optimize media is not enabled, aborting. backsUpMedia: ${WaveStore.backup.backsUpMedia} optimizeStorage: ${WaveStore.backup.optimizeStorage}")
      return Result.success()
    }

    Log.i(TAG, "Canceling any previous restore optimized media jobs and cleanup progress")
    AppDependencies.jobManager.cancelAllInQueues(RestoreAttachmentJob.Queues.OFFLOAD_RESTORE)
    RestoreAttachmentJob.Queues.OFFLOAD_RESTORE.forEach { queue -> AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(queue)) }

    Log.i(TAG, "Optimizing media in the db")

    val available = DiskUtil.getAvailableSpace(context).bytes.toFloat()
    val total = DiskUtil.getTotalDiskSize(context).bytes.toFloat()
    val remaining = (total - available) / total * 100

    val minimumAge = if (remaining > 5f) 30.days else 15.days

    Log.i(TAG, "${"%.1f".format(remaining)}% storage available, not optimizing the last $minimumAge of attachments")

    WaveDatabase.attachments.markEligibleAttachmentsAsOptimized(minimumAge)

    Log.i(TAG, "Deleting abandoned attachment files")
    val count = WaveDatabase.attachments.deleteAbandonedAttachmentFiles()
    Log.i(TAG, "Deleted $count attachments")

    return Result.success()
  }

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  class Factory : Job.Factory<OptimizeMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): OptimizeMediaJob {
      return OptimizeMediaJob(parameters)
    }
  }
}

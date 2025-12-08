/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Marks all call links as needing to be synced by storage service.
 */
internal class SyncCallLinksMigrationJob @JvmOverloads constructor(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {

  companion object {
    const val KEY = "SyncCallLinksMigrationJob"

    private val TAG = Log.tag(SyncCallLinksMigrationJob::class)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (WaveStore.account.aci == null) {
      Log.w(TAG, "Self not available yet.")
      return
    }

    val callLinkRecipients = WaveDatabase.callLinks.getAll().map { it.recipientId }.filter {
      try {
        Recipient.resolved(it)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Unable to resolve recipient: $it")
        false
      }
    }

    WaveDatabase.recipients.markNeedsSync(callLinkRecipients)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<SyncCallLinksMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncCallLinksMigrationJob {
      return SyncCallLinksMigrationJob(parameters)
    }
  }
}

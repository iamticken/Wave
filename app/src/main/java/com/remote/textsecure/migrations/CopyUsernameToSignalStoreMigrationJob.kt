package org.thoughtcrime.securesms.migrations

import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Migration to copy any existing username to [WaveStore.account]
 */
internal class CopyUsernameToWaveStoreMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "CopyUsernameToWaveStore"

    val TAG = Log.tag(CopyUsernameToWaveStoreMigrationJob::class.java)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (WaveStore.account.aci == null || WaveStore.account.pni == null) {
      Log.i(TAG, "ACI/PNI are unset, skipping.")
      return
    }

    val self = Recipient.self()

    if (self.username.isEmpty || self.username.get().isBlank()) {
      Log.i(TAG, "No username set, skipping.")
      return
    }

    WaveStore.account.username = self.username.get()

    // New fields in storage service, so we trigger a sync
    WaveDatabase.recipients.markNeedsSync(self.id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<CopyUsernameToWaveStoreMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CopyUsernameToWaveStoreMigrationJob {
      return CopyUsernameToWaveStoreMigrationJob(parameters)
    }
  }
}

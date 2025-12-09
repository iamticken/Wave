package org.thoughtcrime.securesms.migrations

import org.wave.core.util.logging.Log
import org.wave.core.util.readToList
import org.wave.core.util.requireLong
import org.wave.core.util.select
import org.thoughtcrime.securesms.database.ChatFolderTables
import org.thoughtcrime.securesms.database.ChatFolderTables.ChatFolderTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Marks all chat folders as needing to be synced for storage service.
 */
internal class SyncChatFoldersMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    const val KEY = "SyncChatFoldersMigrationJob"

    private val TAG = Log.tag(SyncChatFoldersMigrationJob::class)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val folderIds = WaveDatabase.chatFolders.getAllFoldersForSync()

    WaveDatabase.chatFolders.markNeedsSync(folderIds)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun ChatFolderTables.getAllFoldersForSync(): List<Long> {
    return readableDatabase
      .select(ChatFolderTable.ID)
      .from(ChatFolderTable.TABLE_NAME)
      .run()
      .readToList { cursor -> cursor.requireLong(ChatFolderTable.ID) }
  }

  class Factory : Job.Factory<SyncChatFoldersMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncChatFoldersMigrationJob {
      return SyncChatFoldersMigrationJob(parameters)
    }
  }
}

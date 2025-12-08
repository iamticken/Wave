package org.thoughtcrime.securesms.components.settings.app.chats.folders

import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Repository for chat folders that handles creation, deletion, listing, etc.,
 */
object ChatFoldersRepository {

  fun getCurrentFolders(): List<ChatFolderRecord> {
    return WaveDatabase.chatFolders.getCurrentChatFolders()
  }

  fun getUnreadCountAndEmptyAndMutedStatusForFolders(folders: List<ChatFolderRecord>): HashMap<Long, Triple<Int, Boolean, Boolean>> {
    return WaveDatabase.chatFolders.getUnreadCountAndEmptyAndMutedStatusForFolders(folders)
  }

  fun createFolder(folder: ChatFolderRecord, includedRecipients: Set<Recipient>, excludedRecipients: Set<Recipient>) {
    val includedChats = includedRecipients.map { recipient -> WaveDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = excludedRecipients.map { recipient -> WaveDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    WaveDatabase.chatFolders.createFolder(updatedFolder)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun updateFolder(folder: ChatFolderRecord, includedRecipients: Set<Recipient>, excludedRecipients: Set<Recipient>) {
    val includedChats = includedRecipients.map { recipient -> WaveDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = excludedRecipients.map { recipient -> WaveDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    WaveDatabase.chatFolders.updateFolder(updatedFolder)
    scheduleSync(updatedFolder.id)
  }

  fun deleteFolder(folder: ChatFolderRecord) {
    WaveDatabase.chatFolders.deleteChatFolder(folder)
    scheduleSync(folder.id)
  }

  fun updatePositions(folders: List<ChatFolderRecord>) {
    WaveDatabase.chatFolders.updatePositions(folders)
    folders.forEach { scheduleSync(it.id) }
  }

  fun getFolder(id: Long): ChatFolderRecord {
    return WaveDatabase.chatFolders.getChatFolder(id)!!
  }

  fun getFolderCount(): Int {
    return WaveDatabase.chatFolders.getFolderCount()
  }

  private fun scheduleSync(id: Long) {
    WaveDatabase.chatFolders.markNeedsSync(id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }
}

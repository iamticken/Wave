/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wave.core.models.ServiceId.ACI
import org.wave.core.util.UuidUtil
import org.wave.core.util.deleteAll
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderId
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testing.WaveActivityRule
import org.whispersystems.waveservice.api.storage.WaveChatFolderRecord
import org.whispersystems.waveservice.api.storage.StorageId
import java.util.UUID
import org.whispersystems.waveservice.internal.storage.protos.ChatFolderRecord as RemoteChatFolderRecord
import org.whispersystems.waveservice.internal.storage.protos.Recipient as RemoteRecipient

@RunWith(AndroidJUnit4::class)
class ChatFolderTablesTest {

  @get:Rule
  val harness = WaveActivityRule()

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId
  private lateinit var charlie: RecipientId

  private lateinit var folder1: ChatFolderRecord
  private lateinit var folder2: ChatFolderRecord
  private lateinit var folder3: ChatFolderRecord
  private lateinit var folder4: ChatFolderRecord

  private lateinit var recipientIds: List<RecipientId>

  private var aliceThread: Long = 0
  private var bobThread: Long = 0
  private var charlieThread: Long = 0

  @Before
  fun setUp() {
    recipientIds = createRecipients(5)

    alice = recipientIds[0]
    bob = recipientIds[1]
    charlie = recipientIds[2]

    aliceThread = WaveDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    bobThread = WaveDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(bob))
    charlieThread = WaveDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(charlie))

    folder1 = ChatFolderRecord(
      id = 2,
      name = "folder1",
      position = 0,
      includedChats = listOf(aliceThread, bobThread),
      excludedChats = listOf(charlieThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.CUSTOM,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(1, 2, 3))
    )

    folder2 = ChatFolderRecord(
      name = "folder2",
      position = 2,
      includedChats = listOf(bobThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.INDIVIDUAL,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(2, 3, 4))
    )

    folder3 = ChatFolderRecord(
      name = "folder3",
      position = 3,
      includedChats = listOf(bobThread),
      excludedChats = listOf(aliceThread, charlieThread),
      showUnread = true,
      showMutedChats = true,
      showGroupChats = true,
      folderType = ChatFolderRecord.FolderType.GROUP,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(3, 4, 5))
    )

    folder4 = ChatFolderRecord(
      name = "folder4",
      position = 4,
      excludedChats = listOf(aliceThread, charlieThread),
      showUnread = true,
      showMutedChats = true,
      showGroupChats = true,
      folderType = ChatFolderRecord.FolderType.UNREAD,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(4, 5, 6))
    )

    WaveDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderTable.TABLE_NAME)
    WaveDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderMembershipTable.TABLE_NAME)
  }

  @Test
  fun givenChatFolder_whenIGetFolder_thenIExpectFolderWithChats() {
    WaveDatabase.chatFolders.createFolder(folder1)
    val actualFolders = WaveDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolder_whenIUpdateFolder_thenIExpectUpdatedFolderWithChats() {
    WaveDatabase.chatFolders.createFolder(folder2)
    val folder = WaveDatabase.chatFolders.getCurrentChatFolders().first()
    val updatedFolder = folder.copy(
      name = "updatedFolder2",
      position = 1,
      includedChats = listOf(aliceThread, charlieThread),
      excludedChats = listOf(bobThread)
    )
    WaveDatabase.chatFolders.updateFolder(updatedFolder)

    val actualFolder = WaveDatabase.chatFolders.getCurrentChatFolders().first()

    assertEquals(updatedFolder, actualFolder)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetFolders_thenIExpectAListWithoutThatFolder() {
    WaveDatabase.chatFolders.createFolder(folder1)
    WaveDatabase.chatFolders.createFolder(folder2)
    val folders = WaveDatabase.chatFolders.getCurrentChatFolders()
    WaveDatabase.chatFolders.deleteChatFolder(folders.last())

    val actualFolders = WaveDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolders_whenIUpdateTheirStorageSyncIds_thenIExpectAnUpdatedList() {
    val existingMap = WaveDatabase.chatFolders.getStorageSyncIdsMap()
    existingMap.forEach { (id, _) ->
      WaveDatabase.chatFolders.applyStorageIdUpdate(id, StorageId.forChatFolder(StorageSyncHelper.generateKey()))
    }
    val updatedMap = WaveDatabase.chatFolders.getStorageSyncIdsMap()

    existingMap.forEach { (id, storageId) ->
      assertNotEquals(storageId, updatedMap[id])
    }
  }

  @Test
  fun givenARemoteFolder_whenIInsertLocally_thenIExpectAListWithThatFolder() {
    val remoteRecord =
      WaveChatFolderRecord(
        folder1.storageServiceId!!,
        RemoteChatFolderRecord(
          identifier = UuidUtil.toByteArray(folder1.chatFolderId.uuid).toByteString(),
          name = folder1.name,
          position = folder1.position,
          showOnlyUnread = folder1.showUnread,
          showMutedChats = folder1.showMutedChats,
          includeAllIndividualChats = folder1.showIndividualChats,
          includeAllGroupChats = folder1.showGroupChats,
          folderType = RemoteChatFolderRecord.FolderType.CUSTOM,
          deletedAtTimestampMs = folder1.deletedTimestampMs,
          includedRecipients = listOf(
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(alice).serviceId.get().toString())),
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(bob).serviceId.get().toString()))
          ),
          excludedRecipients = listOf(
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(charlie).serviceId.get().toString()))
          )

        )
      )

    WaveDatabase.chatFolders.insertChatFolderFromStorageSync(remoteRecord)
    val actualFolders = WaveDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetPositions_thenIExpectPositionsToStillBeConsecutive() {
    WaveDatabase.chatFolders.createFolder(folder1)
    WaveDatabase.chatFolders.createFolder(folder2)
    WaveDatabase.chatFolders.createFolder(folder3)

    val folders = WaveDatabase.chatFolders.getCurrentChatFolders()
    WaveDatabase.chatFolders.deleteChatFolder(folders[1])

    val actualFolders = WaveDatabase.chatFolders.getCurrentChatFolders()
    actualFolders.forEachIndexed { index, folder ->
      assertEquals(folder.position, index)
    }
  }

  @Test
  fun givenAnEmptyFolder_whenIGetItsEmptyStatus_thenIExpectTrue() {
    WaveDatabase.chatFolders.createFolder(folder4)
    val actualFolders = WaveDatabase.chatFolders.getCurrentChatFolders()
    val unreadCountAndEmptyAndMutedStatus = WaveDatabase.chatFolders.getUnreadCountAndEmptyAndMutedStatusForFolders(actualFolders)
    val actualFolderIsEmpty = unreadCountAndEmptyAndMutedStatus[actualFolders.first().id]!!.second

    assertTrue(actualFolderIsEmpty)
  }

  private fun createRecipients(count: Int): List<RecipientId> {
    return (1..count).map {
      WaveDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    }
  }
}

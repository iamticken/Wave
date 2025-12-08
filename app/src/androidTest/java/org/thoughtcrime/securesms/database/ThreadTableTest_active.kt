/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.wave.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.WaveDatabaseRule
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.UUID

@Suppress("ClassName")
class ThreadTableTest_active {

  @Rule
  @JvmField
  val databaseRule = WaveDatabaseRule()

  private lateinit var recipient: Recipient
  private val allChats: ChatFolderRecord = ChatFolderRecord(folderType = ChatFolderRecord.FolderType.ALL)

  @Before
  fun setUp() {
    mockkStatic(RemoteConfig::class)

    every { RemoteConfig.showChatFolders } returns true

    recipient = Recipient.resolved(WaveDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID())))
  }

  @Test
  fun givenActiveUnarchivedThread_whenIGetUnarchivedConversationList_thenIExpectThread() {
    val threadId = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    WaveDatabase.threads.update(threadId, false)

    WaveDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10,
      allChats
    ).use { threads ->
      assertEquals(1, threads.count)

      val record = ThreadTable.StaticReader(threads, InstrumentationRegistry.getInstrumentation().context).getNext()

      assertNotNull(record)
      assertEquals(record!!.recipient.id, recipient.id)
    }
  }

  @Test
  fun givenInactiveUnarchivedThread_whenIGetUnarchivedConversationList_thenIExpectNoThread() {
    val threadId = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    WaveDatabase.threads.update(threadId, false)
    WaveDatabase.threads.deleteConversation(threadId)

    WaveDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10,
      allChats
    ).use { threads ->
      assertEquals(0, threads.count)
    }

    val threadId2 = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    assertEquals(threadId2, threadId)
  }

  @Test
  fun givenActiveArchivedThread_whenIGetUnarchivedConversationList_thenIExpectNoThread() {
    val threadId = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    WaveDatabase.threads.update(threadId, false)
    WaveDatabase.threads.setArchived(setOf(threadId), true)

    WaveDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10,
      allChats
    ).use { threads ->
      assertEquals(0, threads.count)
    }
  }

  @Test
  fun givenActiveArchivedThread_whenIGetArchivedConversationList_thenIExpectThread() {
    val threadId = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    WaveDatabase.threads.update(threadId, false)
    WaveDatabase.threads.setArchived(setOf(threadId), true)

    WaveDatabase.threads.getArchivedConversationList(
      ConversationFilter.OFF,
      0,
      10
    ).use { threads ->
      assertEquals(1, threads.count)
    }
  }

  @Test
  fun givenInactiveArchivedThread_whenIGetArchivedConversationList_thenIExpectNoThread() {
    val threadId = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    WaveDatabase.threads.update(threadId, false)
    WaveDatabase.threads.deleteConversation(threadId)
    WaveDatabase.threads.setArchived(setOf(threadId), true)

    WaveDatabase.threads.getArchivedConversationList(
      ConversationFilter.OFF,
      0,
      10
    ).use { threads ->
      assertEquals(0, threads.count)
    }

    val threadId2 = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    assertEquals(threadId2, threadId)
  }

  @Test
  fun givenActiveArchivedThread_whenIDeactivateThread_thenIExpectNoMessages() {
    val threadId = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    WaveDatabase.threads.update(threadId, false)

    WaveDatabase.messages.getConversation(threadId).use {
      assertEquals(1, it.count)
    }

    WaveDatabase.threads.deleteConversation(threadId)

    WaveDatabase.messages.getConversation(threadId).use {
      assertEquals(0, it.count)
    }
  }
}

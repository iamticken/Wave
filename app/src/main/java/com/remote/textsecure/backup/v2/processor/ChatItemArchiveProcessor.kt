/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.database.createChatItemInserter
import org.thoughtcrime.securesms.backup.v2.database.getMessagesForBackup
import org.thoughtcrime.securesms.backup.v2.importer.ChatItemArchiveImporter
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Handles importing/exporting [ChatItem] frames for an archive.
 */
object ChatItemArchiveProcessor {
  val TAG = Log.tag(ChatItemArchiveProcessor::class.java)

  fun export(db: WaveDatabase, exportState: ExportState, selfRecipientId: RecipientId, messageInclusionCutoffTime: Long, cancellationWave: () -> Boolean, emitter: BackupFrameEmitter) {
    db.messageTable.getMessagesForBackup(db, exportState.backupTime, selfRecipientId, messageInclusionCutoffTime, exportState).use { chatItems ->
      var count = 0
      while (chatItems.hasNext()) {
        if (count % 1000 == 0 && cancellationWave()) {
          return@use
        }

        val chatItem: ChatItem? = chatItems.next()
        if (chatItem != null) {
          if (exportState.threadIds.contains(chatItem.chatId)) {
            emitter.emit(Frame(chatItem = chatItem))
          }
        }
        count++
      }
    }
  }

  fun beginImport(importState: ImportState): ChatItemArchiveImporter {
    return WaveDatabase.messages.createChatItemInserter(importState)
  }
}

package org.wave.benchmark.setup

import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.TestDbUtils
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.waveservice.api.messages.WaveServiceAttachment
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId
import java.util.Collections
import java.util.Optional

object TestMessages {
  fun insertOutgoingTextMessage(other: Recipient, body: String, timestamp: Long = System.currentTimeMillis()) {
    insertOutgoingMessage(
      recipient = other,
      message = OutgoingMessage(
        recipient = other,
        body = body,
        timestamp = timestamp,
        isSecure = true
      ),
      timestamp = timestamp
    )
  }

  fun insertOutgoingImageMessage(other: Recipient, body: String? = null, attachmentCount: Int, timestamp: Long = System.currentTimeMillis()): Long {
    val attachments: List<WaveServiceAttachmentPointer> = (0 until attachmentCount).map {
      imageAttachment()
    }
    val message = OutgoingMessage(
      recipient = other,
      body = body,
      attachments = PointerAttachment.forPointers(Optional.of(attachments)),
      timestamp = timestamp,
      isSecure = true
    )
    return insertOutgoingMediaMessage(recipient = other, message = message, timestamp = timestamp)
  }

  private fun insertOutgoingMediaMessage(recipient: Recipient, message: OutgoingMessage, timestamp: Long): Long {
    val insert = insertOutgoingMessage(recipient, message = message, timestamp = timestamp)
    setMessageMediaTransfered(insert)

    return insert
  }

  private fun insertOutgoingMessage(recipient: Recipient, message: OutgoingMessage, timestamp: Long? = null): Long {
    val insert = WaveDatabase.messages.insertMessageOutbox(
      message,
      WaveDatabase.threads.getOrCreateThreadIdFor(recipient),
      false,
      null
    )
    if (timestamp != null) {
      TestDbUtils.setMessageReceived(insert.messageId, timestamp)
    }
    WaveDatabase.messages.markAsSent(insert.messageId, true)

    return insert.messageId
  }
  fun insertIncomingTextMessage(other: Recipient, body: String, timestamp: Long? = null) {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      body = body,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis()
    )

    WaveDatabase.messages.insertMessageInbox(message, WaveDatabase.threads.getOrCreateThreadIdFor(other)).get().messageId
  }
  fun insertIncomingQuoteTextMessage(other: Recipient, body: String, quote: QuoteModel, timestamp: Long?) {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      body = body,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis(),
      quote = quote
    )
    insertIncomingMessage(other, message = message)
  }
  fun insertIncomingImageMessage(other: Recipient, body: String? = null, attachmentCount: Int, timestamp: Long? = null, failed: Boolean = false): Long {
    val attachments: List<WaveServiceAttachmentPointer> = (0 until attachmentCount).map {
      imageAttachment()
    }
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis(),
      attachments = PointerAttachment.forPointers(Optional.of(attachments))
    )
    return insertIncomingMessage(recipient = other, message = message, failed = failed)
  }

  fun insertIncomingVoiceMessage(other: Recipient, timestamp: Long? = null): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis(),
      attachments = PointerAttachment.forPointers(Optional.of(Collections.singletonList(voiceAttachment()) as List<WaveServiceAttachment>))
    )
    return insertIncomingMessage(recipient = other, message = message, failed = false)
  }

  private fun insertIncomingMessage(recipient: Recipient, message: IncomingMessage, failed: Boolean = false): Long {
    val id = insertIncomingMessage(recipient = recipient, message = message)
    if (failed) {
      setMessageMediaFailed(id)
    } else {
      setMessageMediaTransfered(id)
    }

    return id
  }

  private fun insertIncomingMessage(recipient: Recipient, message: IncomingMessage): Long {
    return WaveDatabase.messages.insertMessageInbox(message, WaveDatabase.threads.getOrCreateThreadIdFor(recipient)).get().messageId
  }

  private fun setMessageMediaFailed(messageId: Long) {
    WaveDatabase.attachments.getAttachmentsForMessage(messageId).forEachIndexed { index, attachment ->
      WaveDatabase.attachments.setTransferProgressPermanentFailure(attachment.attachmentId, messageId)
    }
  }

  private fun setMessageMediaTransfered(messageId: Long) {
    WaveDatabase.attachments.getAttachmentsForMessage(messageId).forEachIndexed { _, attachment ->
      WaveDatabase.attachments.setTransferState(messageId, attachment.attachmentId, AttachmentTable.TRANSFER_PROGRESS_DONE)
    }
  }
  private fun imageAttachment(): WaveServiceAttachmentPointer {
    return WaveServiceAttachmentPointer(
      Cdn.S3.cdnNumber,
      WaveServiceAttachmentRemoteId.from(""),
      "image/webp",
      null,
      Optional.empty(),
      Optional.empty(),
      1024,
      1024,
      Optional.empty(),
      Optional.empty(),
      0,
      Optional.of("/not-there.jpg"),
      false,
      false,
      false,
      Optional.empty(),
      Optional.empty(),
      System.currentTimeMillis(),
      null
    )
  }

  private fun voiceAttachment(): WaveServiceAttachmentPointer {
    return WaveServiceAttachmentPointer(
      Cdn.S3.cdnNumber,
      WaveServiceAttachmentRemoteId.from(""),
      "audio/aac",
      null,
      Optional.empty(),
      Optional.empty(),
      1024,
      1024,
      Optional.empty(),
      Optional.empty(),
      0,
      Optional.of("/not-there.aac"),
      true,
      false,
      false,
      Optional.empty(),
      Optional.empty(),
      System.currentTimeMillis(),
      null
    )
  }

  class TimestampGenerator(private var start: Long = System.currentTimeMillis()) {
    fun nextTimestamp(): Long {
      start += 500L

      return start
    }
  }
}

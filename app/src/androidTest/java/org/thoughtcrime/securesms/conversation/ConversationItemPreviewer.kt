package org.thoughtcrime.securesms.conversation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wave.core.util.ThreadUtil
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.conversation.v2.ConversationActivity
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.WaveActivityRule
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId
import java.util.Optional

/**
 * Helper test for rendering conversation items for preview.
 */
@RunWith(AndroidJUnit4::class)
@Ignore("For testing/previewing manually, no assertions")
class ConversationItemPreviewer {

  @get:Rule
  val harness = WaveActivityRule(othersCount = 10)

  @Test
  fun testShowLongName() {
    val other: Recipient = Recipient.resolved(harness.others.first())

    WaveDatabase.recipients.setProfileName(other.id, ProfileName.fromParts("Seef", "$$$"))

    insertFailedMediaMessage(other = other, attachmentCount = 1)
    insertFailedMediaMessage(other = other, attachmentCount = 2)
    insertFailedMediaMessage(other = other, body = "Test", attachmentCount = 1)
//    insertFailedOutgoingMediaMessage(other = other, body = "Test", attachmentCount = 1)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)
//    insertMediaMessage(other = other)

    val scenario: ActivityScenario<ConversationActivity> = harness.launchActivity { putExtra("recipient_id", other.id.serialize()) }
    scenario.onActivity {
    }

    // Uncomment to make dialog stay on screen, otherwise will show/dismiss immediately
//    ThreadUtil.sleep(45000)
  }

  private fun insertMediaMessage(other: Recipient, body: String? = null, attachmentCount: Int = 1) {
    val attachments: List<WaveServiceAttachmentPointer> = (0 until attachmentCount).map {
      attachment()
    }

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      body = body,
      sentTimeMillis = System.currentTimeMillis(),
      serverTimeMillis = System.currentTimeMillis(),
      receivedTimeMillis = System.currentTimeMillis(),
      attachments = PointerAttachment.forPointers(Optional.of(attachments))
    )

    WaveDatabase.messages.insertMessageInbox(message, WaveDatabase.threads.getOrCreateThreadIdFor(other)).get()

    ThreadUtil.sleep(1)
  }

  private fun insertFailedMediaMessage(other: Recipient, body: String? = null, attachmentCount: Int = 1) {
    val attachments: List<WaveServiceAttachmentPointer> = (0 until attachmentCount).map {
      attachment()
    }

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      body = body,
      sentTimeMillis = System.currentTimeMillis(),
      serverTimeMillis = System.currentTimeMillis(),
      receivedTimeMillis = System.currentTimeMillis(),
      attachments = PointerAttachment.forPointers(Optional.of(attachments))
    )

    val insert = WaveDatabase.messages.insertMessageInbox(message, WaveDatabase.threads.getOrCreateThreadIdFor(other)).get()

    WaveDatabase.attachments.getAttachmentsForMessage(insert.messageId).forEachIndexed { index, attachment ->
//      if (index != 1) {
      WaveDatabase.attachments.setTransferProgressPermanentFailure(attachment.attachmentId, insert.messageId)
//      } else {
//        WaveDatabase.attachments.setTransferState(insert.messageId, attachment, TRANSFER_PROGRESS_STARTED)
//      }
    }

    ThreadUtil.sleep(1)
  }

  private fun insertFailedOutgoingMediaMessage(other: Recipient, body: String? = null, attachmentCount: Int = 1) {
    val attachments: List<WaveServiceAttachmentPointer> = (0 until attachmentCount).map {
      attachment()
    }

    val message = OutgoingMessage(
      recipient = other,
      body = body,
      attachments = PointerAttachment.forPointers(Optional.of(attachments)),
      timestamp = System.currentTimeMillis(),
      isSecure = true
    )

    val insert = WaveDatabase.messages.insertMessageOutbox(
      message,
      WaveDatabase.threads.getOrCreateThreadIdFor(other),
      false,
      null
    ).messageId

    WaveDatabase.attachments.getAttachmentsForMessage(insert).forEachIndexed { index, attachment ->
      WaveDatabase.attachments.setTransferProgressPermanentFailure(attachment.attachmentId, insert)
    }

    ThreadUtil.sleep(1)
  }

  private fun attachment(): WaveServiceAttachmentPointer {
    return WaveServiceAttachmentPointer(
      Cdn.CDN_3.cdnNumber,
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
}

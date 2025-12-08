package org.thoughtcrime.securesms.releasechannel

import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.waveservice.api.messages.WaveServiceAttachment
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId
import java.util.Optional
import java.util.UUID

/**
 * One stop shop for inserting Release Channel messages.
 */
object ReleaseChannel {

  fun insertReleaseChannelMessage(
    recipientId: RecipientId,
    body: String,
    threadId: Long,
    media: String? = null,
    mediaWidth: Int = 0,
    mediaHeight: Int = 0,
    mediaType: String = "image/webp",
    mediaAttachmentUuid: UUID? = UUID.randomUUID(),
    messageRanges: BodyRangeList? = null,
    storyType: StoryType = StoryType.NONE
  ): MessageTable.InsertResult? {
    val attachments: Optional<List<WaveServiceAttachment>> = if (media != null) {
      val attachment = WaveServiceAttachmentPointer(
        cdnNumber = Cdn.S3.cdnNumber,
        remoteId = WaveServiceAttachmentRemoteId.S3,
        contentType = mediaType,
        key = null,
        size = Optional.empty(),
        preview = Optional.empty(),
        width = mediaWidth,
        height = mediaHeight,
        digest = Optional.empty(),
        incrementalDigest = Optional.empty(),
        incrementalMacChunkSize = 0,
        fileName = Optional.of(media),
        voiceNote = false,
        isBorderless = false,
        isGif = MediaUtil.isVideo(mediaType),
        caption = Optional.empty(),
        blurHash = Optional.empty(),
        uploadTimestamp = System.currentTimeMillis(),
        uuid = mediaAttachmentUuid
      )

      Optional.of(listOf(attachment))
    } else {
      Optional.empty()
    }

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = recipientId,
      sentTimeMillis = System.currentTimeMillis(),
      serverTimeMillis = System.currentTimeMillis(),
      receivedTimeMillis = System.currentTimeMillis(),
      body = body,
      attachments = PointerAttachment.forPointers(attachments),
      serverGuid = UUID.randomUUID().toString(),
      messageRanges = messageRanges,
      storyType = storyType
    )

    return WaveDatabase.messages.insertMessageInbox(message, threadId).orElse(null)
  }
}

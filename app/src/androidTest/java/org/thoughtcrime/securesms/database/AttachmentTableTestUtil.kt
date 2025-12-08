/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import org.wave.core.util.Base64
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.waveservice.api.attachment.AttachmentUploadResult
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId
import kotlin.random.Random

object AttachmentTableTestUtil {

  fun createUploadResult(attachmentId: AttachmentId, uploadTimestamp: Long = System.currentTimeMillis()): AttachmentUploadResult {
    val databaseAttachment = WaveDatabase.attachments.getAttachment(attachmentId)!!

    return AttachmentUploadResult(
      remoteId = WaveServiceAttachmentRemoteId.V4("somewhere-${Random.nextLong()}"),
      cdnNumber = Cdn.CDN_3.cdnNumber,
      key = databaseAttachment.remoteKey?.let { Base64.decode(it) } ?: Util.getSecretBytes(64),
      digest = Random.nextBytes(32),
      incrementalDigest = Random.nextBytes(16),
      incrementalDigestChunkSize = 5,
      uploadTimestamp = uploadTimestamp,
      dataSize = databaseAttachment.size,
      blurHash = databaseAttachment.blurHash?.hash
    )
  }
}

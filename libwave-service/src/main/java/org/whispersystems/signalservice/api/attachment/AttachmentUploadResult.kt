/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.attachment

import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId

/**
 * The result of uploading an attachment. Just the additional metadata related to the upload itself.
 */
class AttachmentUploadResult(
  val remoteId: WaveServiceAttachmentRemoteId,
  val cdnNumber: Int,
  val key: ByteArray,
  val digest: ByteArray,
  val incrementalDigest: ByteArray?,
  val incrementalDigestChunkSize: Int,
  val dataSize: Long,
  val uploadTimestamp: Long,
  val blurHash: String?
)

/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.waveservice.api.messages

import java.util.Optional
import java.util.UUID

/**
 * Represents a received WaveServiceAttachment "handle."  This
 * is a pointer to the actual attachment content, which needs to be
 * retrieved using [WaveServiceMessageReceiver.retrieveAttachment]
 *
 * @author Moxie Marlinspike
 */
class WaveServiceAttachmentPointer(
  val cdnNumber: Int,
  val remoteId: WaveServiceAttachmentRemoteId,
  contentType: String?,
  val key: ByteArray?,
  val size: Optional<Int>,
  val preview: Optional<ByteArray>,
  val width: Int,
  val height: Int,
  val digest: Optional<ByteArray>,
  val incrementalDigest: Optional<ByteArray>,
  val incrementalMacChunkSize: Int,
  val fileName: Optional<String>,
  val voiceNote: Boolean,
  val isBorderless: Boolean,
  val isGif: Boolean,
  val caption: Optional<String>,
  val blurHash: Optional<String>,
  val uploadTimestamp: Long,
  val uuid: UUID?
) : WaveServiceAttachment(contentType) {
  override fun isStream() = false
  override fun isPointer() = true
}

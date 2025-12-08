/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.waveservice.internal.push

import org.whispersystems.waveservice.api.messages.WaveServiceAttachment
import org.whispersystems.waveservice.internal.push.http.CancelationWave
import org.whispersystems.waveservice.internal.push.http.OutputStreamFactory
import org.whispersystems.waveservice.internal.push.http.ResumableUploadSpec
import java.io.InputStream

/**
 * A bundle of data needed to start an attachment upload.
 */
data class PushAttachmentData(
  val contentType: String?,
  val data: InputStream,
  val dataSize: Long,
  val incremental: Boolean,
  val outputStreamFactory: OutputStreamFactory,
  val listener: WaveServiceAttachment.ProgressListener?,
  val cancelationWave: CancelationWave?,
  val resumableUploadSpec: ResumableUploadSpec
)

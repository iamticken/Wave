/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.crypto

data class AttachmentDigest(
  val digest: ByteArray,
  val incrementalDigest: ByteArray?,
  val incrementalMacChunkSize: Int
)

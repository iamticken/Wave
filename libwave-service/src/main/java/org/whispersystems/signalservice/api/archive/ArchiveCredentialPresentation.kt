/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.archive

import org.wave.core.util.Base64

/**
 * Acts as credentials for various archive operations.
 */
class ArchiveCredentialPresentation(
  val presentation: ByteArray,
  val signedPresentation: ByteArray
) {
  fun toHeaders(): MutableMap<String, String> {
    return mutableMapOf(
      "X-Wave-ZK-Auth" to Base64.encodeWithPadding(presentation),
      "X-Wave-ZK-Auth-Signature" to Base64.encodeWithPadding(signedPresentation)
    )
  }
}

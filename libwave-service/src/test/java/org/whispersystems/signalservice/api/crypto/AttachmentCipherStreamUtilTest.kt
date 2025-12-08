/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.crypto

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wave.core.util.copyTo
import org.whispersystems.waveservice.internal.util.Util
import java.io.ByteArrayOutputStream

class AttachmentCipherStreamUtilTest {

  @Test
  fun `getCiphertextLength should return the correct length`() {
    for (length in 0..1024) {
      val plaintext = ByteArray(length).also { it.fill(0x42) }
      val key = Util.getSecretBytes(64)
      val iv = Util.getSecretBytes(16)

      val outputStream = ByteArrayOutputStream()
      val cipherStream = AttachmentCipherOutputStream(key, iv, outputStream)
      plaintext.inputStream().copyTo(cipherStream)

      val expected = AttachmentCipherStreamUtil.getCiphertextLength(length.toLong())
      val actual = outputStream.size().toLong()

      assertEquals(expected, actual)
    }
  }
}

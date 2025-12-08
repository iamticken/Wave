/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.util

import java.security.SecureRandom

object RandomUtil {

  fun getSecureBytes(size: Int): ByteArray {
    val secret = ByteArray(size)
    SecureRandom().nextBytes(secret)
    return secret
  }
}

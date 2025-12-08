/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import org.thoughtcrime.securesms.util.WaveE164Util

@JvmInline
value class PhoneNumber(val value: String) {
  val displayText: String
    get() = WaveE164Util.prettyPrint(value)
}

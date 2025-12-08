/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.donations

import org.json.JSONObject

/**
 * A PaymentSource, being something that can be used to perform a
 * transaction. See [PaymentSourceType].
 */
interface PaymentSource {
  val type: PaymentSourceType
  fun parameterize(): JSONObject = error("Unsupported by $type.")
  fun getTokenId(): String = error("Unsupported by $type.")
  fun email(): String? = error("Unsupported by $type.")
}

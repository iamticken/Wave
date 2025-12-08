/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.util.billing

class BillingError(
  val billingResponseCode: Int
) : Exception("$billingResponseCode")

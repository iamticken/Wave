/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.util.billing

import org.wave.core.util.money.FiatMoney

/**
 * Represents a purchasable product from the Google Play Billing API
 */
data class BillingProduct(
  val price: FiatMoney
)

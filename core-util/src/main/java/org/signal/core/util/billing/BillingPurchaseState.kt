/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.util.billing

/**
 * BillingPurchaseState which aligns with the Google Play Billing purchased state.
 */
enum class BillingPurchaseState {
  UNSPECIFIED,
  PURCHASED,
  PENDING
}

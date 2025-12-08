/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.util.billing

import android.content.Context

/**
 * Provides a dependency model by which the billing api can request different resources.
 */
interface BillingDependencies {
  /**
   * Application context
   */
  val context: Context

  /**
   * Get the product id from the donations configuration object.
   */
  suspend fun getProductId(): String

  /**
   * Get the base plan id from the donations configuration object.
   */
  suspend fun getBasePlanId(): String
}

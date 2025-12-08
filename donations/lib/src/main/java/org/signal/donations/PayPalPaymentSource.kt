/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.donations

class PayPalPaymentSource : PaymentSource {
  override val type: PaymentSourceType = PaymentSourceType.PayPal
}

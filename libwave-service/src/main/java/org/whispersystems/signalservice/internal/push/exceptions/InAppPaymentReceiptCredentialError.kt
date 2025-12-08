/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push.exceptions

import com.fasterxml.jackson.annotation.JsonCreator
import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.waveservice.api.subscriptions.ActiveSubscription.ChargeFailure

/**
 * HTTP 402 Exception when trying to submit credentials for a donation with
 * a failed payment.
 */
class InAppPaymentReceiptCredentialError @JsonCreator constructor(
  val chargeFailure: ChargeFailure
) : NonSuccessfulResponseCodeException(402) {
  override fun toString(): String {
    return """
      DonationReceiptCredentialError (402)
      Charge Failure: $chargeFailure
    """.trimIndent()
  }
}

/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

class StripeOneTimePaymentIntentPayload {
  @JsonProperty
  private long amount;

  @JsonProperty
  private String currency;

  @JsonProperty
  private long level;

  @JsonProperty
  private String paymentMethod;

  public StripeOneTimePaymentIntentPayload(long amount, String currency, long level, String paymentMethod) {
    this.amount        = amount;
    this.currency      = currency;
    this.level         = level;
    this.paymentMethod = paymentMethod;
  }
}

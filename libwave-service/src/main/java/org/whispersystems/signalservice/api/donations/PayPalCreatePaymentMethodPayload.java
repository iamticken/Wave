/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

class PayPalCreatePaymentMethodPayload {
  @JsonProperty
  private String returnUrl;

  @JsonProperty
  private String cancelUrl;

  PayPalCreatePaymentMethodPayload(String returnUrl, String cancelUrl) {
    this.returnUrl = returnUrl;
    this.cancelUrl = cancelUrl;
  }
}

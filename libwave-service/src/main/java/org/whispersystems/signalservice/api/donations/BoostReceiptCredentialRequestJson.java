/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.wave.libwave.zkgroup.receipts.ReceiptCredentialRequest;
import org.wave.core.util.Base64;
import org.whispersystems.waveservice.internal.push.DonationProcessor;

class BoostReceiptCredentialRequestJson {
  @JsonProperty("paymentIntentId")
  private final String paymentIntentId;

  @JsonProperty("receiptCredentialRequest")
  private final String receiptCredentialRequest;

  @JsonProperty("processor")
  private final String processor;

  BoostReceiptCredentialRequestJson(String paymentIntentId, ReceiptCredentialRequest receiptCredentialRequest, DonationProcessor processor) {
    this.paymentIntentId          = paymentIntentId;
    this.receiptCredentialRequest = Base64.encodeWithPadding(receiptCredentialRequest.serialize());
    this.processor                = processor.getCode();
  }
}

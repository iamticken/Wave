/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.wave.libwave.zkgroup.receipts.ReceiptCredentialRequest;
import org.wave.core.util.Base64;

class ReceiptCredentialRequestJson {
  @JsonProperty("receiptCredentialRequest")
  private final String receiptCredentialRequest;

  ReceiptCredentialRequestJson(ReceiptCredentialRequest receiptCredentialRequest) {
    this.receiptCredentialRequest = Base64.encodeWithPadding(receiptCredentialRequest.serialize());
  }
}

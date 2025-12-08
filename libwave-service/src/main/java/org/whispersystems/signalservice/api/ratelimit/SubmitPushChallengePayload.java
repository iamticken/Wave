/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.ratelimit;

import com.fasterxml.jackson.annotation.JsonProperty;

class SubmitPushChallengePayload {

  @JsonProperty
  private String type;

  @JsonProperty
  private String challenge;

  public SubmitPushChallengePayload() {}

  public SubmitPushChallengePayload(String challenge) {
    this.type      = "rateLimitPushChallenge";
    this.challenge = challenge;
  }
}

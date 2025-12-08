/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.ratelimit;

import com.fasterxml.jackson.annotation.JsonProperty;

class SubmitRecaptchaChallengePayload {

  @JsonProperty
  private String type;

  @JsonProperty
  private String token;

  @JsonProperty
  private String captcha;

  public SubmitRecaptchaChallengePayload() {}

  public SubmitRecaptchaChallengePayload(String challenge, String recaptchaToken) {
    this.type    = "captcha";
    this.token   = challenge;
    this.captcha = recaptchaToken;
  }
}

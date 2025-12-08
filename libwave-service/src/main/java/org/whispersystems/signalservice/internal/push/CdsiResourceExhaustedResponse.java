package org.whispersystems.waveservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for {@link org.whispersystems.waveservice.api.push.exceptions.CdsiResourceExhaustedException}
 */
public class CdsiResourceExhaustedResponse {
  @JsonProperty("retry_after")
  private int retryAfter;

  public int getRetryAfter() {
    return retryAfter;
  }
}

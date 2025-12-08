package org.whispersystems.waveservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Jackson parser for the response body from the server explaining a failure.
 * See also [org.whispersystems.waveservice.api.push.exceptions.ExternalServiceFailureException]
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VerificationCodeFailureResponseBody(
  @JsonProperty("permanentFailure") val permanentFailure: Boolean,
  @JsonProperty("reason") val reason: String
)

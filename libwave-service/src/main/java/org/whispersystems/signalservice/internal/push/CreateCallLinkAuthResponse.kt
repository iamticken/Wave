/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.wave.core.util.Base64
import org.wave.libwave.zkgroup.calllinks.CreateCallLinkCredentialResponse

/**
 * Response body for CreateCallLinkAuthResponse
 */
data class CreateCallLinkAuthResponse @JsonCreator constructor(
  @JsonProperty("credential") val credential: String
) {
  val createCallLinkCredentialResponse: CreateCallLinkCredentialResponse
    get() = CreateCallLinkCredentialResponse(Base64.decode(credential))
}

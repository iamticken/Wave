/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.link

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request body for setting the name of a linked device.
 */
data class SetDeviceNameRequest(
  @JsonProperty val deviceName: String
)

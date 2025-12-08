/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.waveservice.api.messages.calls.TurnServerInfo

/**
 * Response body for GetCallingRelays
 */
data class GetCallingRelaysResponse @JsonCreator constructor(
  @JsonProperty("relays") val relays: List<TurnServerInfo>?
)

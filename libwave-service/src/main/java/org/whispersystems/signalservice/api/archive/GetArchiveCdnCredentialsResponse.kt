/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Get response with headers to use to read from archive cdn.
 */
class GetArchiveCdnCredentialsResponse(
  @JsonProperty val headers: Map<String, String>
)

/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Multi-response data for a batch archive media operation.
 */
class BatchArchiveMediaResponse(
  @JsonProperty val responses: List<BatchArchiveMediaItemResponse>
) {
  class BatchArchiveMediaItemResponse(
    @JsonProperty val status: Int?,
    @JsonProperty val failureReason: String?,
    @JsonProperty val cdn: Int?,
    @JsonProperty val mediaId: String
  )
}

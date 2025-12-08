/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Delete media from the backup cdn.
 */
class DeleteArchivedMediaRequest(
  @JsonProperty val mediaToDelete: List<ArchivedMediaObject>
) {
  data class ArchivedMediaObject(
    @JsonProperty val cdn: Int,
    @JsonProperty val mediaId: String
  )
}

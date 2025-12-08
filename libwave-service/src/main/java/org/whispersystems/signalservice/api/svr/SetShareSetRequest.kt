/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.svr

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.whispersystems.waveservice.internal.push.ByteArraySerializerBase64NoPadding

/**
 * Request body for setting a share-set on the service.
 */
class SetShareSetRequest(
  @JsonProperty
  @JsonSerialize(using = ByteArraySerializerBase64NoPadding::class)
  val shareSet: ByteArray
)

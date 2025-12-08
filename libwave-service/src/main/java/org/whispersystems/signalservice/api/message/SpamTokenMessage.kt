/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.message

import com.fasterxml.jackson.annotation.JsonProperty

data class SpamTokenMessage(@JsonProperty val token: String?)

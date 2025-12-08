/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Localized bank transfer mandate.
 */
class BankMandate @JsonCreator constructor(@JsonProperty("mandate") val mandate: String)

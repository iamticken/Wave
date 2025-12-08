/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.pincreation

data class PinCreationState(
  val isNumericKeyboard: Boolean = true,
  val inputLabel: String? = null,
  val isConfirmEnabled: Boolean = false
)

/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.pinentry

data class PinEntryState(
  val errorMessage: String? = null,
  val showNeedHelp: Boolean = false,
  val isNumericKeyboard: Boolean = true
)

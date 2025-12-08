/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.pincreation

sealed class PinCreationScreenEvents {
  data class PinSubmitted(val pin: String) : PinCreationScreenEvents()
  data object ToggleKeyboard : PinCreationScreenEvents()
  data object LearnMore : PinCreationScreenEvents()
}

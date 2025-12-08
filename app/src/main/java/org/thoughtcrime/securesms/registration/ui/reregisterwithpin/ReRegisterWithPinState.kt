/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType

data class ReRegisterWithPinState(
  val isLocalVerification: Boolean = false,
  val hasIncorrectGuess: Boolean = false,
  val localPinMatches: Boolean = false,
  val pinKeyboardType: PinKeyboardType = WaveStore.pin.keyboardType
)

/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.verificationcode

sealed class VerificationCodeScreenEvents {
  data class CodeEntered(val code: String) : VerificationCodeScreenEvents()
  data object WrongNumber : VerificationCodeScreenEvents()
  data object ResendSms : VerificationCodeScreenEvents()
  data object CallMe : VerificationCodeScreenEvents()
  data object HavingTrouble : VerificationCodeScreenEvents()
  data object ConsumeInnerOneTimeEvent : VerificationCodeScreenEvents()
}

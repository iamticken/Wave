/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.verificationcode

import org.wave.registration.NetworkController.SessionMetadata
import kotlin.time.Duration

data class VerificationCodeState(
  val sessionMetadata: SessionMetadata? = null,
  val e164: String = "",
  val oneTimeEvent: OneTimeEvent? = null
) {
  sealed interface OneTimeEvent {
    data object NetworkError : OneTimeEvent
    data object UnknownError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object ThirdPartyError : OneTimeEvent
    data object CouldNotRequestCodeWithSelectedTransport : OneTimeEvent
    data object IncorrectVerificationCode : OneTimeEvent
    data object RegistrationError : OneTimeEvent
  }
}

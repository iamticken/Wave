/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.captcha

sealed class CaptchaScreenEvents {
  data class CaptchaCompleted(val token: String) : CaptchaScreenEvents()
  data object Cancel : CaptchaScreenEvents()
}

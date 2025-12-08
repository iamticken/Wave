/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.captcha

sealed class CaptchaLoadState {
  data object Loading : CaptchaLoadState()
  data object Loaded : CaptchaLoadState()
  data object Error : CaptchaLoadState()
}

data class CaptchaState(
  val captchaUrl: String,
  val captchaScheme: String = "wavecaptcha://",
  val loadState: CaptchaLoadState = CaptchaLoadState.Loading
)

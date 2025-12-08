/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push.exceptions

import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException

/**
 * Indicates that the captcha we submitted was not accepted by the server.
 */
class CaptchaRejectedException : NonSuccessfulResponseCodeException(428, "Captcha rejected by server.")

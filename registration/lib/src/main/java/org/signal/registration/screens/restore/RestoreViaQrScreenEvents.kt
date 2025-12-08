/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.restore

sealed class RestoreViaQrScreenEvents {
  data object RetryQrCode : RestoreViaQrScreenEvents()
  data object Cancel : RestoreViaQrScreenEvents()
  data object UseProxy : RestoreViaQrScreenEvents()
  data object DismissError : RestoreViaQrScreenEvents()
}

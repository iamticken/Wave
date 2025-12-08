/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.restore

import org.wave.core.ui.compose.QrCodeData

sealed class QrState {
  data object Loading : QrState()
  data class Loaded(val qrCodeData: QrCodeData) : QrState()
  data object Scanned : QrState()
  data object Failed : QrState()
}

data class RestoreViaQrState(
  val qrState: QrState = QrState.Loading,
  val isRegistering: Boolean = false,
  val showRegistrationError: Boolean = false,
  val errorMessage: String? = null
)

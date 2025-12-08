/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.sample.screens.main

data class MainScreenState(
  val existingRegistrationState: ExistingRegistrationState? = null
) {
  data class ExistingRegistrationState(val phoneNumber: String)
}

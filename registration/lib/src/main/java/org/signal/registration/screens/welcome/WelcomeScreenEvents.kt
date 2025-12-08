/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.welcome

sealed class WelcomeScreenEvents {
  data object Continue : WelcomeScreenEvents()
  data object HasOldPhone : WelcomeScreenEvents()
  data object DoesNotHaveOldPhone : WelcomeScreenEvents()
}

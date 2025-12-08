/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.sample.screens.main

sealed interface MainScreenEvents {
  data object LaunchRegistration : MainScreenEvents
}

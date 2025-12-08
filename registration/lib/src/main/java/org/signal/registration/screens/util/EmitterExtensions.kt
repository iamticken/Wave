/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.util

import org.wave.registration.RegistrationFlowEvent
import org.wave.registration.RegistrationRoute

fun ((RegistrationFlowEvent) -> Unit).navigateTo(route: RegistrationRoute) {
  this(RegistrationFlowEvent.NavigateToScreen(route))
}

fun ((RegistrationFlowEvent) -> Unit).navigateBack() {
  this(RegistrationFlowEvent.NavigateBack)
}

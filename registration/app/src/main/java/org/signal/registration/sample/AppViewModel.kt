/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.sample

import androidx.lifecycle.ViewModel
import org.wave.core.ui.navigation.ResultEventBus

class AppViewModel : ViewModel() {
  val resultEventBus = ResultEventBus()
}

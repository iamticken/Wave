/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.wave.core.ui.compose.theme.ExtendedColors
import org.thoughtcrime.securesms.util.TextSecurePreferences

private typealias CoreWaveTheme = org.wave.core.ui.compose.theme.WaveTheme

@Composable
fun WaveTheme(
  isDarkMode: Boolean = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val incognitoKeyboardEnabled = remember {
    TextSecurePreferences.isIncognitoKeyboardEnabled(context)
  }

  org.wave.core.ui.compose.theme.WaveTheme(
    isDarkMode = isDarkMode,
    incognitoKeyboardEnabled = incognitoKeyboardEnabled,
    content = content
  )
}

object WaveTheme {
  val colors: ExtendedColors
    @Composable
    get() = CoreWaveTheme.colors
}

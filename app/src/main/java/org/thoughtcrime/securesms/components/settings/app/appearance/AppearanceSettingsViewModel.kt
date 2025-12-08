package org.thoughtcrime.securesms.components.settings.app.appearance

import android.app.Activity
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob
import org.thoughtcrime.securesms.keyvalue.SettingsValues.Theme
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.SplashScreenUtil

class AppearanceSettingsViewModel : ViewModel() {
  private val store = MutableStateFlow(getState())
  val state: StateFlow<AppearanceSettingsState> = store

  fun refreshState() {
    store.update { getState() }
  }

  fun setTheme(activity: Activity?, theme: Theme) {
    store.update { it.copy(theme = theme) }
    WaveStore.settings.theme = theme
    SplashScreenUtil.setSplashScreenThemeIfNecessary(activity, theme)
  }

  fun setLanguage(language: String) {
    store.update { it.copy(language = language) }
    WaveStore.settings.language = language
    EmojiSearchIndexDownloadJob.scheduleImmediately()
  }

  fun setMessageFontSize(size: Int) {
    store.update { it.copy(messageFontSize = size) }
    WaveStore.settings.messageFontSize = size
  }

  private fun getState(): AppearanceSettingsState {
    return AppearanceSettingsState(
      WaveStore.settings.theme,
      WaveStore.settings.messageFontSize,
      WaveStore.settings.language,
      WaveStore.settings.useCompactNavigationBar
    )
  }
}

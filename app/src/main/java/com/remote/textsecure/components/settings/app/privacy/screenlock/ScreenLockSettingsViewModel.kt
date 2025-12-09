package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.keyvalue.WaveStore

/**
 * Maintains the state of the [ScreenLockSettingsFragment]
 */
class ScreenLockSettingsViewModel : ViewModel() {

  private val _state = MutableStateFlow(getState())
  val state = _state.asStateFlow()

  fun toggleScreenLock() {
    val enabled = !_state.value.screenLock
    WaveStore.settings.screenLockEnabled = enabled
    _state.update {
      it.copy(
        screenLock = enabled
      )
    }
  }

  fun setScreenLockTimeout(seconds: Long) {
    WaveStore.settings.screenLockTimeout = seconds
    _state.update {
      it.copy(
        screenLockActivityTimeout = seconds
      )
    }
  }

  private fun getState(): ScreenLockSettingsState {
    return ScreenLockSettingsState(
      screenLock = WaveStore.settings.screenLockEnabled,
      screenLockActivityTimeout = WaveStore.settings.screenLockTimeout
    )
  }
}

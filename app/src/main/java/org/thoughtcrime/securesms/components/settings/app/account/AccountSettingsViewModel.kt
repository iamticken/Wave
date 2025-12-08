package org.thoughtcrime.securesms.components.settings.app.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

class AccountSettingsViewModel : ViewModel() {
  private val store: MutableStateFlow<AccountSettingsState> = MutableStateFlow(getCurrentState())

  val state: StateFlow<AccountSettingsState> = store

  fun refreshState() {
    store.update { getCurrentState() }
  }

  fun togglePinKeyboardType() {
    store.update {
      it.copy(pinKeyboardType = it.pinKeyboardType.other)
    }
  }

  private fun getCurrentState(): AccountSettingsState {
    return AccountSettingsState(
      hasPin = WaveStore.svr.hasPin() && !WaveStore.svr.hasOptedOut(),
      pinKeyboardType = WaveStore.pin.keyboardType,
      hasRestoredAep = WaveStore.account.restoredAccountEntropyPool,
      pinRemindersEnabled = WaveStore.pin.arePinRemindersEnabled() && WaveStore.svr.hasPin(),
      registrationLockEnabled = WaveStore.svr.isRegistrationLockEnabled,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      clientDeprecated = WaveStore.misc.isClientDeprecated,
      canTransferWhileUnregistered = true
    )
  }
}

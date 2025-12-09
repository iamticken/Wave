package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import org.wave.donations.InAppPaymentType
import org.thoughtcrime.securesms.database.InAppPaymentTable

sealed class DonateToWaveAction {
  data class DisplayCurrencySelectionDialog(val inAppPaymentType: InAppPaymentType, val supportedCurrencies: List<String>) : DonateToWaveAction()
  data class DisplayGatewaySelectorDialog(val inAppPayment: InAppPaymentTable.InAppPayment) : DonateToWaveAction()
  data object CancelSubscription : DonateToWaveAction()
  data class UpdateSubscription(val inAppPayment: InAppPaymentTable.InAppPayment, val isLongRunning: Boolean) : DonateToWaveAction()
}

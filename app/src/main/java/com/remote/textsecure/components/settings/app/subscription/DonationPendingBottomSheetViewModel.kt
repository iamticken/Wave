/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wave.core.util.concurrent.WaveDispatchers
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.WaveDatabase

class DonationPendingBottomSheetViewModel(
  inAppPaymentId: InAppPaymentTable.InAppPaymentId
) : ViewModel() {

  private val internalInAppPayment = MutableStateFlow<InAppPaymentTable.InAppPayment?>(null)
  val inAppPayment: StateFlow<InAppPaymentTable.InAppPayment?> = internalInAppPayment

  init {
    viewModelScope.launch {
      val inAppPayment = withContext(WaveDispatchers.IO) {
        WaveDatabase.inAppPayments.getById(inAppPaymentId)!!
      }

      internalInAppPayment.update { inAppPayment }
    }
  }
}

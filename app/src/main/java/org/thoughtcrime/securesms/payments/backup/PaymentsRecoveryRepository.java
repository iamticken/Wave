package org.thoughtcrime.securesms.payments.backup;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.payments.Mnemonic;

public final class PaymentsRecoveryRepository {
  public @NonNull Mnemonic getMnemonic() {
    return WaveStore.payments().getPaymentsMnemonic();
  }
}

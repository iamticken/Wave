package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.whispersystems.waveservice.api.WaveServiceDataStore;
import org.wave.core.models.ServiceId;

public final class WaveServiceDataStoreImpl implements WaveServiceDataStore {

  private final Context                           context;
  private final WaveServiceAccountDataStoreImpl aciStore;
  private final WaveServiceAccountDataStoreImpl pniStore;

  public WaveServiceDataStoreImpl(@NonNull Context context,
                                    @NonNull WaveServiceAccountDataStoreImpl aciStore,
                                    @NonNull WaveServiceAccountDataStoreImpl pniStore)
  {
    this.context  = context;
    this.aciStore = aciStore;
    this.pniStore = pniStore;
  }

  @Override
  public WaveServiceAccountDataStoreImpl get(@NonNull ServiceId accountIdentifier) {
    if (accountIdentifier.equals(WaveStore.account().getAci())) {
      return aciStore;
    } else if (accountIdentifier.equals(WaveStore.account().getPni())) {
      return pniStore;
    } else {
      throw new IllegalArgumentException("No matching store found for " + accountIdentifier);
    }
  }

  @Override
  public WaveServiceAccountDataStoreImpl aci() {
    return aciStore;
  }

  @Override
  public WaveServiceAccountDataStoreImpl pni() {
    return pniStore;
  }

  @Override
  public boolean isMultiDevice() {
    return WaveStore.account().isMultiDevice();
  }
}

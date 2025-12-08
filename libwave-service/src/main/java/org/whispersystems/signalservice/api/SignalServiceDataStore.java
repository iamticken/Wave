package org.whispersystems.waveservice.api;

import org.wave.core.models.ServiceId;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface WaveServiceDataStore {

  /**
   * @return A {@link WaveServiceAccountDataStore} for the specified account.
   */
  WaveServiceAccountDataStore get(ServiceId accountIdentifier);

  /**
   * @return A {@link WaveServiceAccountDataStore} for the ACI account.
   */
  WaveServiceAccountDataStore aci();

  /**
   * @return A {@link WaveServiceAccountDataStore} for the PNI account.
   */
  WaveServiceAccountDataStore pni();

  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();
}

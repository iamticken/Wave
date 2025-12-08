package org.whispersystems.waveservice.api;

import org.wave.libwave.protocol.state.WaveProtocolStore;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface WaveServiceAccountDataStore extends WaveProtocolStore,
                                                       WaveServicePreKeyStore,
                                                       WaveServiceSessionStore,
                                                       WaveServiceSenderKeyStore,
                                                       WaveServiceKyberPreKeyStore {
  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();
}

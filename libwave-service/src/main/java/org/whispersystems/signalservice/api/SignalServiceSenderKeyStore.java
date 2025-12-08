package org.whispersystems.waveservice.api;

import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.groups.state.SenderKeyStore;
import org.whispersystems.waveservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Set;

/**
 * And extension of the normal protocol sender key store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
public interface WaveServiceSenderKeyStore extends SenderKeyStore {
  /**
   * @return A set of protocol addresses that have previously been sent the sender key data for the provided distributionId.
   */
  Set<WaveProtocolAddress> getSenderKeySharedWith(DistributionId distributionId);

  /**
   * Marks the provided addresses as having been sent the sender key data for the provided distributionId.
   */
  void markSenderKeySharedWith(DistributionId distributionId, Collection<WaveProtocolAddress> addresses);

  /**
   * Marks the provided addresses as not knowing about any distributionIds.
   */
  void clearSenderKeySharedWith(Collection<WaveProtocolAddress> addresses);
}

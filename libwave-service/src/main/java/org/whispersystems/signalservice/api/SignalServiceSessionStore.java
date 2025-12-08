package org.whispersystems.waveservice.api;

import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.state.SessionRecord;
import org.wave.libwave.protocol.state.SessionStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * And extension of the normal protocol session store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
public interface WaveServiceSessionStore extends SessionStore {
  void archiveSession(WaveProtocolAddress address);
  Map<WaveProtocolAddress, SessionRecord> getAllAddressesWithActiveSessions(List<String> addressNames);
}

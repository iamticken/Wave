package org.whispersystems.waveservice.api.crypto;

import org.wave.libwave.protocol.SessionBuilder;
import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.groups.GroupSessionBuilder;
import org.wave.libwave.protocol.message.SenderKeyDistributionMessage;
import org.whispersystems.waveservice.api.WaveSessionLock;

import java.util.UUID;

/**
 * A thread-safe wrapper around {@link SessionBuilder}.
 */
public class WaveGroupSessionBuilder {

  private final WaveSessionLock   lock;
  private final GroupSessionBuilder builder;

  public WaveGroupSessionBuilder(WaveSessionLock lock, GroupSessionBuilder builder) {
    this.lock    = lock;
    this.builder = builder;
  }

  public void process(WaveProtocolAddress sender, SenderKeyDistributionMessage senderKeyDistributionMessage) {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      builder.process(sender, senderKeyDistributionMessage);
    }
  }

  public SenderKeyDistributionMessage create(WaveProtocolAddress sender, UUID distributionId) {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return builder.create(sender, distributionId);
    }
  }
}

package org.whispersystems.waveservice.api.crypto;

import org.wave.libwave.protocol.InvalidKeyException;
import org.wave.libwave.protocol.SessionBuilder;
import org.wave.libwave.protocol.UntrustedIdentityException;
import org.wave.libwave.protocol.state.PreKeyBundle;
import org.whispersystems.waveservice.api.WaveSessionLock;

/**
 * A thread-safe wrapper around {@link SessionBuilder}.
 */
public class WaveSessionBuilder {

  private final WaveSessionLock lock;
  private final SessionBuilder    builder;

  public WaveSessionBuilder(WaveSessionLock lock, SessionBuilder builder) {
    this.lock    = lock;
    this.builder = builder;
  }

  public void process(PreKeyBundle preKey) throws InvalidKeyException, UntrustedIdentityException {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      builder.process(preKey);
    }
  }
}

package org.whispersystems.waveservice.api;

import java.io.Closeable;

/**
 * An interface to allow the injection of a lock that will be used to keep interactions with
 * ecryptions/decryptions thread-safe.
 */
public interface WaveSessionLock {

  Lock acquire();

  interface Lock extends Closeable {
    @Override
    void close();
  }
}

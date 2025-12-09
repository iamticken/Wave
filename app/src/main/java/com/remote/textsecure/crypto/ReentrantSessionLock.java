package org.thoughtcrime.securesms.crypto;

import org.whispersystems.waveservice.api.WaveSessionLock;

import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of {@link WaveSessionLock} that is backed by a {@link ReentrantLock}.
 */
public enum ReentrantSessionLock implements WaveSessionLock {

  INSTANCE;

  private static final ReentrantLock LOCK = new ReentrantLock();

  @Override
  public Lock acquire() {
    LOCK.lock();
    return LOCK::unlock;
  }

  public boolean isHeldByCurrentThread() {
    return LOCK.isHeldByCurrentThread();
  }
}

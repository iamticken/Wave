package org.whispersystems.waveservice.api.util;

public interface SleepTimer {
  public void sleep(long millis) throws InterruptedException;
}

package org.whispersystems.waveservice.internal.push.http;

/**
 * Used to communicate to observers whether or not something is canceled.
 */
public interface CancelationWave {
  boolean isCanceled();
}

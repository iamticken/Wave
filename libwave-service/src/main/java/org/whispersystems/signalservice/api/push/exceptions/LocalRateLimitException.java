package org.whispersystems.waveservice.api.push.exceptions;

/**
 * Thrown when self limiting networking.
 */
public final class LocalRateLimitException extends Exception {
  public LocalRateLimitException() { }
}

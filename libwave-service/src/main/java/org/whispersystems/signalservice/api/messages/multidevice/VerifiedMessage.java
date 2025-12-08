package org.whispersystems.waveservice.api.messages.multidevice;


import org.wave.libwave.protocol.IdentityKey;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;

public class VerifiedMessage {

  public enum VerifiedState {
    DEFAULT, VERIFIED, UNVERIFIED
  }

  private final WaveServiceAddress destination;
  private final IdentityKey          identityKey;
  private final VerifiedState        verified;
  private final long                 timestamp;

  public VerifiedMessage(WaveServiceAddress destination, IdentityKey identityKey, VerifiedState verified, long timestamp) {
    this.destination = destination;
    this.identityKey = identityKey;
    this.verified    = verified;
    this.timestamp   = timestamp;
  }

  public WaveServiceAddress getDestination() {
    return destination;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public VerifiedState getVerified() {
    return verified;
  }

  public long getTimestamp() {
    return timestamp;
  }
}

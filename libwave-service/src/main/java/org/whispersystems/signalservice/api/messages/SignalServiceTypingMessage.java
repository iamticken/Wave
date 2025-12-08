package org.whispersystems.waveservice.api.messages;


import java.util.Optional;

public class WaveServiceTypingMessage {

  public enum Action {
    UNKNOWN, STARTED, STOPPED
  }

  private final Action           action;
  private final long             timestamp;
  private final Optional<byte[]> groupId;

  public WaveServiceTypingMessage(Action action, long timestamp, Optional<byte[]> groupId) {
    this.action    = action;
    this.timestamp = timestamp;
    this.groupId   = groupId;
  }

  public Action getAction() {
    return action;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Optional<byte[]> getGroupId() {
    return groupId;
  }

  public boolean isTypingStarted() {
    return action == Action.STARTED;
  }

  public boolean isTypingStopped() {
    return action == Action.STOPPED;
  }
}

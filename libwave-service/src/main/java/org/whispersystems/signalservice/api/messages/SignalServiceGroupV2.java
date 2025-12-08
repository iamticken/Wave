package org.whispersystems.waveservice.api.messages;

import org.wave.libwave.zkgroup.InvalidInputException;
import org.wave.libwave.zkgroup.groups.GroupMasterKey;
import org.whispersystems.waveservice.api.util.Preconditions;
import org.whispersystems.waveservice.internal.push.GroupContextV2;

import io.reactivex.rxjava3.annotations.NonNull;

/**
 * Group information to include in WaveServiceMessages destined to v2 groups.
 * <p>
 * This class represents a "context" that is included with Wave Service messages
 * to make them group messages.
 */
public final class WaveServiceGroupV2 {

  private final GroupMasterKey masterKey;
  private final int            revision;
  private final byte[]         signedGroupChange;

  private WaveServiceGroupV2(Builder builder) {
    this.masterKey         = builder.masterKey;
    this.revision          = builder.revision;
    this.signedGroupChange = builder.signedGroupChange != null ? builder.signedGroupChange.clone() : null;
  }

  /**
   * Creates a context model populated from a protobuf group V2 context.
   */
  public static WaveServiceGroupV2 fromProtobuf(@NonNull GroupContextV2 groupContextV2) {
    Preconditions.checkArgument(groupContextV2.masterKey != null && groupContextV2.revision != null);

    GroupMasterKey masterKey;
    try {
      masterKey = new GroupMasterKey(groupContextV2.masterKey.toByteArray());
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }

    Builder builder = newBuilder(masterKey);

    if (groupContextV2.groupChange != null && groupContextV2.groupChange.size() > 0) {
      builder.withSignedGroupChange(groupContextV2.groupChange.toByteArray());
    }

    return builder.withRevision(groupContextV2.revision)
                  .build();
  }

  public GroupMasterKey getMasterKey() {
    return masterKey;
  }

  public int getRevision() {
    return revision;
  }

  public byte[] getSignedGroupChange() {
    return signedGroupChange;
  }

  public boolean hasSignedGroupChange() {
    return signedGroupChange != null && signedGroupChange.length > 0;
  }

  public static Builder newBuilder(GroupMasterKey masterKey) {
    return new Builder(masterKey);
  }

  public static class Builder {

    private final GroupMasterKey masterKey;
    private       int            revision;
    private       byte[]         signedGroupChange;

    private Builder(GroupMasterKey masterKey) {
      if (masterKey == null) {
        throw new IllegalArgumentException();
      }
      this.masterKey = masterKey;
    }

    public Builder withRevision(int revision) {
      this.revision = revision;
      return this;
    }

    public Builder withSignedGroupChange(byte[] signedGroupChange) {
      this.signedGroupChange = signedGroupChange;
      return this;
    }

    public WaveServiceGroupV2 build() {
      return new WaveServiceGroupV2(this);
    }
  }
}

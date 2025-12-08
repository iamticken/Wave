package org.whispersystems.waveservice.api.messages;


import org.whispersystems.waveservice.internal.push.BodyRange;

import java.util.List;
import java.util.Optional;

public class WaveServiceStoryMessage {
  private final Optional<byte[]>                      profileKey;
  private final Optional<WaveServiceGroupV2>        groupContext;
  private final Optional<WaveServiceAttachment>     fileAttachment;
  private final Optional<WaveServiceTextAttachment> textAttachment;
  private final Optional<Boolean>                     allowsReplies;
  private final Optional<List<BodyRange>>             bodyRanges;

  private WaveServiceStoryMessage(byte[] profileKey,
                                    WaveServiceGroupV2 groupContext,
                                    WaveServiceAttachment fileAttachment,
                                    WaveServiceTextAttachment textAttachment,
                                    boolean allowsReplies,
                                    List<BodyRange> bodyRanges)
  {
    this.profileKey     = Optional.ofNullable(profileKey);
    this.groupContext   = Optional.ofNullable(groupContext);
    this.fileAttachment = Optional.ofNullable(fileAttachment);
    this.textAttachment = Optional.ofNullable(textAttachment);
    this.allowsReplies  = Optional.of(allowsReplies);
    this.bodyRanges     = Optional.ofNullable(bodyRanges);
  }

  public static WaveServiceStoryMessage forFileAttachment(byte[] profileKey,
                                                            WaveServiceGroupV2 groupContext,
                                                            WaveServiceAttachment fileAttachment,
                                                            boolean allowsReplies,
                                                            List<BodyRange> bodyRanges)
  {
    return new WaveServiceStoryMessage(profileKey, groupContext, fileAttachment, null, allowsReplies, bodyRanges);
  }

  public static WaveServiceStoryMessage forTextAttachment(byte[] profileKey,
                                                            WaveServiceGroupV2 groupContext,
                                                            WaveServiceTextAttachment textAttachment,
                                                            boolean allowsReplies,
                                                            List<BodyRange> bodyRanges)
  {
    return new WaveServiceStoryMessage(profileKey, groupContext, null, textAttachment, allowsReplies, bodyRanges);
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<WaveServiceGroupV2> getGroupContext() {
    return groupContext;
  }

  public Optional<WaveServiceAttachment> getFileAttachment() {
    return fileAttachment;
  }

  public Optional<WaveServiceTextAttachment> getTextAttachment() {
    return textAttachment;
  }

  public Optional<Boolean> getAllowsReplies() {
    return allowsReplies;
  }

  public Optional<List<BodyRange>> getBodyRanges() {
    return bodyRanges;
  }
}

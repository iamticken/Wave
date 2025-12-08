package org.whispersystems.waveservice.api.messages;

import org.whispersystems.waveservice.api.push.WaveServiceAddress;

import java.util.List;

public class WaveServiceStoryMessageRecipient {

  private final WaveServiceAddress waveServiceAddress;
  private final List<String>         distributionListIds;
  private final boolean              isAllowedToReply;

  public WaveServiceStoryMessageRecipient(WaveServiceAddress waveServiceAddress,
                                            List<String> distributionListIds,
                                            boolean isAllowedToReply)
  {
    this.waveServiceAddress = waveServiceAddress;
    this.distributionListIds  = distributionListIds;
    this.isAllowedToReply     = isAllowedToReply;
  }

  public List<String> getDistributionListIds() {
    return distributionListIds;
  }

  public WaveServiceAddress getWaveServiceAddress() {
    return waveServiceAddress;
  }

  public boolean isAllowedToReply() {
    return isAllowedToReply;
  }
}

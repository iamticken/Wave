/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.waveservice.api.messages.multidevice;


import org.whispersystems.waveservice.api.messages.WaveServiceDataMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceEditMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceStoryMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceStoryMessageRecipient;
import org.wave.core.models.ServiceId;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SentTranscriptMessage {

  private final Optional<WaveServiceAddress>          destination;
  private final long                                    timestamp;
  private final long                                    expirationStartTimestamp;
  private final Optional<WaveServiceDataMessage>      message;
  private final Map<ServiceId, Boolean>                 unidentifiedStatusBySid;
  private final Set<ServiceId>                          recipients;
  private final boolean                                 isRecipientUpdate;
  private final Optional<WaveServiceStoryMessage>     storyMessage;
  private final Set<WaveServiceStoryMessageRecipient> storyMessageRecipients;
  private final Optional<WaveServiceEditMessage>      editMessage;

  public SentTranscriptMessage(Optional<WaveServiceAddress> destination,
                               long timestamp,
                               Optional<WaveServiceDataMessage> message,
                               long expirationStartTimestamp,
                               Map<ServiceId, Boolean> unidentifiedStatus,
                               boolean isRecipientUpdate,
                               Optional<WaveServiceStoryMessage> storyMessage,
                               Set<WaveServiceStoryMessageRecipient> storyMessageRecipients,
                               Optional<WaveServiceEditMessage> editMessage)
  {
    this.destination              = destination;
    this.timestamp                = timestamp;
    this.message                  = message;
    this.expirationStartTimestamp = expirationStartTimestamp;
    this.unidentifiedStatusBySid  = new HashMap<>(unidentifiedStatus);
    this.recipients               = unidentifiedStatus.keySet();
    this.isRecipientUpdate        = isRecipientUpdate;
    this.storyMessage             = storyMessage;
    this.storyMessageRecipients   = storyMessageRecipients;
    this.editMessage              = editMessage;
  }

  public Optional<WaveServiceAddress> getDestination() {
    return destination;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getExpirationStartTimestamp() {
    return expirationStartTimestamp;
  }

  public Optional<WaveServiceDataMessage> getDataMessage() {
    return message;
  }

  public Optional<WaveServiceEditMessage> getEditMessage() {
    return editMessage;
  }

  public Optional<WaveServiceStoryMessage> getStoryMessage() {
    return storyMessage;
  }

  public Set<WaveServiceStoryMessageRecipient> getStoryMessageRecipients() {
    return storyMessageRecipients;
  }

  public boolean isUnidentified(ServiceId serviceId) {
    return unidentifiedStatusBySid.getOrDefault(serviceId, false);
  }

  public Set<ServiceId> getRecipients() {
    return recipients;
  }

  public boolean isRecipientUpdate() {
    return isRecipientUpdate;
  }
}

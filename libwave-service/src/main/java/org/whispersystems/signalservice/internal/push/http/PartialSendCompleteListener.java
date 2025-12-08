package org.whispersystems.waveservice.internal.push.http;

import org.whispersystems.waveservice.api.messages.SendMessageResult;

/**
 * Used to let a listener know when each individual send in a collection of sends has been completed.
 */
public interface PartialSendCompleteListener {
  void onPartialSendComplete(SendMessageResult result);
}

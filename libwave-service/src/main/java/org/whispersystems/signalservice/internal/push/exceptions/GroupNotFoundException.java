package org.whispersystems.waveservice.internal.push.exceptions;

import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class GroupNotFoundException extends NonSuccessfulResponseCodeException {
  public GroupNotFoundException() {
    super(404);
  }
}

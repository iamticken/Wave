package org.whispersystems.waveservice.internal.push.exceptions;

import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class GroupExistsException extends NonSuccessfulResponseCodeException {
  public GroupExistsException() {
    super(409);
  }
}

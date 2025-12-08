package org.whispersystems.waveservice.internal.push.exceptions;

import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class NotInGroupException extends NonSuccessfulResponseCodeException {
  public NotInGroupException() {
    super(403);
  }
}

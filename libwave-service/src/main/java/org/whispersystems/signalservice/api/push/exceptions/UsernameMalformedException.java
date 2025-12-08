package org.whispersystems.waveservice.api.push.exceptions;

public class UsernameMalformedException extends NonSuccessfulResponseCodeException {
  public UsernameMalformedException() {
    super(400);
  }
}

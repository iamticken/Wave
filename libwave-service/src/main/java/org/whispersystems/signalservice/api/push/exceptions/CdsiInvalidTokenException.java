package org.whispersystems.waveservice.api.push.exceptions;


/**
 * Indicates that you provided a bad token to CDSI.
 */
public class CdsiInvalidTokenException extends NonSuccessfulResponseCodeException {
  public CdsiInvalidTokenException() {
    super(4101);
  }
}

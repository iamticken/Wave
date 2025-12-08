package org.whispersystems.waveservice.internal.push.exceptions;

import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.util.function.Function;

import okhttp3.ResponseBody;

public final class PaymentsRegionException extends NonSuccessfulResponseCodeException {
  public PaymentsRegionException(int code) {
    super(code);
  }
}

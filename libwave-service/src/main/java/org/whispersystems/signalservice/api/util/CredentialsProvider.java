/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.waveservice.api.util;

import org.wave.core.models.ServiceId.ACI;
import org.wave.core.models.ServiceId.PNI;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;

public interface CredentialsProvider {
  ACI getAci();
  PNI getPni();
  String getE164();
  int getDeviceId();
  String getPassword();

  default boolean isInvalid() {
    return getAci() == null || getPassword() == null;
  }

  default String getUsername() {
    StringBuilder sb = new StringBuilder();
    sb.append(getAci().toString());
    if (getDeviceId() != WaveServiceAddress.DEFAULT_DEVICE_ID) {
      sb.append(".");
      sb.append(getDeviceId());
    }
    return sb.toString();
  }
}

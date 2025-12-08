package org.whispersystems.waveservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.waveservice.api.messages.multidevice.DeviceInfo;

import java.util.List;

public class DeviceInfoList {

  @JsonProperty
  public List<DeviceInfo> devices;

  public DeviceInfoList() {}

  public List<DeviceInfo> getDevices() {
    return devices;
  }
}

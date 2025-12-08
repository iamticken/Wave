package org.whispersystems.waveservice.api.profiles;

import org.wave.libwave.zkgroup.profiles.ExpiringProfileKeyCredential;

import java.util.Optional;


public final class ProfileAndCredential {

  private final WaveServiceProfile                   profile;
  private final WaveServiceProfile.RequestType       requestType;
  private final Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential;

  public ProfileAndCredential(WaveServiceProfile profile,
                              WaveServiceProfile.RequestType requestType,
                              Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential)
  {
    this.profile                      = profile;
    this.requestType                  = requestType;
    this.expiringProfileKeyCredential = expiringProfileKeyCredential;
  }

  public WaveServiceProfile getProfile() {
    return profile;
  }

  public WaveServiceProfile.RequestType getRequestType() {
    return requestType;
  }

  public Optional<ExpiringProfileKeyCredential> getExpiringProfileKeyCredential() {
    return expiringProfileKeyCredential;
  }
}

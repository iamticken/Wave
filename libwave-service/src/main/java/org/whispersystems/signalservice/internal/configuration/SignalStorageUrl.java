package org.whispersystems.waveservice.internal.configuration;


import org.whispersystems.waveservice.api.push.TrustStore;

import okhttp3.ConnectionSpec;

public class WaveStorageUrl extends WaveUrl {

  public WaveStorageUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public WaveStorageUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}

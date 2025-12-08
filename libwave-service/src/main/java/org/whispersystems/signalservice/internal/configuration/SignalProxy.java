package org.whispersystems.waveservice.internal.configuration;

public class WaveProxy {
  private final String host;
  private final int    port;

  public WaveProxy(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }
}

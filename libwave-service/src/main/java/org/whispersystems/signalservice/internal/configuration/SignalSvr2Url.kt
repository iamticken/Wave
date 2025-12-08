package org.whispersystems.waveservice.internal.configuration

import okhttp3.ConnectionSpec
import org.whispersystems.waveservice.api.push.TrustStore

/**
 * Configuration for reach the SVR2 service.
 */
class WaveSvr2Url(
  url: String,
  trustStore: TrustStore,
  hostHeader: String? = null,
  connectionSpec: ConnectionSpec? = null
) : WaveUrl(url, hostHeader, trustStore, connectionSpec)

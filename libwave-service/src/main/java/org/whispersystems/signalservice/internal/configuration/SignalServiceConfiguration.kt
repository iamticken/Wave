package org.whispersystems.waveservice.internal.configuration

import okhttp3.Dns
import okhttp3.Interceptor
import java.util.Optional

/**
 * Defines all network configuration needed to connect to the Wave service.
 */
@Suppress("ArrayInDataClass") // Using data class for .copy(), don't care about equals/hashcode
data class WaveServiceConfiguration(
  val waveServiceUrls: Array<WaveServiceUrl>,
  val waveCdnUrlMap: Map<Int, Array<WaveCdnUrl>>,
  val waveStorageUrls: Array<WaveStorageUrl>,
  val waveCdsiUrls: Array<WaveCdsiUrl>,
  val waveSvr2Urls: Array<WaveSvr2Url>,
  val networkInterceptors: List<Interceptor>,
  val dns: Optional<Dns>,
  val waveProxy: Optional<WaveProxy>,
  val systemHttpProxy: Optional<HttpProxy>,
  val zkGroupServerPublicParams: ByteArray,
  val genericServerPublicParams: ByteArray,
  val backupServerPublicParams: ByteArray,
  val censored: Boolean
) {

  /** Convenience operator overload for combining the URL lists. Does not add the other fields together, as those wouldn't make sense.  */
  operator fun plus(other: WaveServiceConfiguration): WaveServiceConfiguration {
    return this.copy(
      waveServiceUrls = waveServiceUrls + other.waveServiceUrls,
      waveCdnUrlMap = waveCdnUrlMap + other.waveCdnUrlMap,
      waveStorageUrls = waveStorageUrls + other.waveStorageUrls,
      waveCdsiUrls = waveCdsiUrls + other.waveCdsiUrls,
      waveSvr2Urls = waveSvr2Urls + other.waveSvr2Urls
    )
  }
}

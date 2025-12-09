package org.thoughtcrime.securesms.net

import okhttp3.Interceptor
import okhttp3.Response
import org.wave.core.util.logging.Log
import org.wave.core.util.logging.Log.tag
import org.wave.core.util.orNull
import org.thoughtcrime.securesms.keyvalue.WaveStore.Companion.misc
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration
import java.io.IOException

/**
 * Marks the client as remotely-deprecated when it receives a 499 response.
 */
class RemoteDeprecationDetectorInterceptor(private val getConfiguration: () -> WaveServiceConfiguration) : Interceptor {

  companion object {
    private val TAG = tag(RemoteDeprecationDetectorInterceptor::class.java)
  }

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val response = chain.proceed(request)

    if (response.code == 499 && !misc.isClientDeprecated && getConfiguration().waveServiceUrls.any { request.url.toString().startsWith(it.url) && it.hostHeader.orNull() == request.header("host") }) {
      Log.w(TAG, "Received 499. Client version is deprecated.", true)
      misc.isClientDeprecated = true
    }

    return response
  }
}

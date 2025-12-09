package org.thoughtcrime.securesms.jobs

import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.stories.Stories
import org.whispersystems.waveservice.api.websocket.WebSocketConnectionState
import org.whispersystems.waveservice.internal.util.StaticCredentialsProvider
import org.whispersystems.waveservice.internal.websocket.OkHttpWebSocketConnection
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Checks to see if a censored user can establish a websocket connection with an uncensored network configuration.
 */
class CheckServiceReachabilityJob private constructor(params: Parameters) : BaseJob(params) {

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.HOURS.toMillis(12))
      .setMaxAttempts(1)
      .build()
  )

  companion object {
    private val TAG = Log.tag(CheckServiceReachabilityJob::class.java)

    const val KEY = "CheckServiceReachabilityJob"

    @JvmStatic
    fun enqueueIfNecessary() {
      val isCensored = AppDependencies.waveServiceNetworkAccess.isCensored()
      val timeSinceLastCheck = System.currentTimeMillis() - WaveStore.misc.lastCensorshipServiceReachabilityCheckTime
      if (WaveStore.account.isRegistered && isCensored && timeSinceLastCheck > TimeUnit.DAYS.toMillis(1)) {
        AppDependencies.jobManager.add(CheckServiceReachabilityJob())
      }
    }
  }

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    if (!WaveStore.account.isRegistered) {
      Log.w(TAG, "Not registered, skipping.")
      WaveStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()
      return
    }

    if (!AppDependencies.waveServiceNetworkAccess.isCensored()) {
      Log.w(TAG, "Not currently censored, skipping.")
      WaveStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()
      return
    }

    WaveStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()

    val uncensoredWebsocket = OkHttpWebSocketConnection(
      "uncensored-test",
      AppDependencies.waveServiceNetworkAccess.uncensoredConfiguration,
      Optional.of(
        StaticCredentialsProvider(
          WaveStore.account.aci,
          WaveStore.account.pni,
          WaveStore.account.e164,
          WaveStore.account.deviceId,
          WaveStore.account.servicePassword
        )
      ),
      BuildConfig.SIGNAL_AGENT,
      null,
      "",
      Stories.isFeatureEnabled()
    )

    try {
      val startTime = System.currentTimeMillis()

      val state: WebSocketConnectionState = uncensoredWebsocket.connect()
        .filter { it == WebSocketConnectionState.CONNECTED || it == WebSocketConnectionState.FAILED }
        .timeout(30, TimeUnit.SECONDS)
        .blockingFirst(WebSocketConnectionState.FAILED)

      if (state == WebSocketConnectionState.CONNECTED) {
        Log.i(TAG, "Established connection in ${System.currentTimeMillis() - startTime} ms! Service is reachable!")
        WaveStore.misc.isServiceReachableWithoutCircumvention = true
      } else {
        Log.w(TAG, "Failed to establish a connection in ${System.currentTimeMillis() - startTime} ms.")
        WaveStore.misc.isServiceReachableWithoutCircumvention = false
      }
    } catch (exception: Exception) {
      Log.w(TAG, "Failed to connect to the websocket.", exception)
      WaveStore.misc.isServiceReachableWithoutCircumvention = false
    } finally {
      uncensoredWebsocket.disconnect()
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return false
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<CheckServiceReachabilityJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CheckServiceReachabilityJob {
      return CheckServiceReachabilityJob(parameters)
    }
  }
}

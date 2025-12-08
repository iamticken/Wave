package org.thoughtcrime.securesms.jobs

import org.wave.core.util.isNotNullOrBlank
import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.net.WaveNetwork
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import kotlin.time.Duration.Companion.days

/**
 * Job to refresh remote configs. Utilizes eTags so a 304 is returned if content is unchanged since last fetch.
 */
class RemoteConfigRefreshJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    const val KEY: String = "RemoteConfigRefreshJob"
    private val TAG = Log.tag(RemoteConfigRefreshJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!WaveStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.success()
    }

    return when (val result = WaveNetwork.remoteConfig.getRemoteConfig(WaveStore.remoteConfig.eTag)) {
      is NetworkResult.Success -> {
        RemoteConfig.update(result.result.config)
        WaveStore.misc.setLastKnownServerTime(result.result.serverEpochTimeMilliseconds, System.currentTimeMillis())
        if (result.result.eTag.isNotNullOrBlank()) {
          WaveStore.remoteConfig.eTag = result.result.eTag
        }
        Result.success()
      }

      is NetworkResult.ApplicationError -> Result.failure()
      is NetworkResult.NetworkError -> Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError ->
        if (result.code == 304) {
          Log.i(TAG, "Remote config has not changed since last pull.")
          WaveStore.remoteConfig.lastFetchTime = System.currentTimeMillis()
          WaveStore.misc.setLastKnownServerTime(result.header(WaveWebSocket.SERVER_DELIVERED_TIMESTAMP_HEADER)?.toLongOrNull() ?: System.currentTimeMillis(), System.currentTimeMillis())
          Result.success()
        } else {
          Result.retry(defaultBackoff())
        }
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RemoteConfigRefreshJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RemoteConfigRefreshJob {
      return RemoteConfigRefreshJob(parameters)
    }
  }
}

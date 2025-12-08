package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import org.wave.core.util.asListContains
import org.wave.ringrtc.AudioConfig
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.webrtc.audio.AudioDeviceConfig

/**
 * Utility class to determine the audio configuration that RingRTC should use.
 */
object RingRtcDynamicConfiguration {
  private var lastFetchTime: Long = 0

  fun isTelecomAllowedForDevice(): Boolean {
    return RemoteConfig.telecomManufacturerAllowList.lowercase().asListContains(Build.MANUFACTURER.lowercase()) &&
      !RemoteConfig.telecomModelBlocklist.lowercase().asListContains(Build.MODEL.lowercase())
  }

  @JvmStatic
  fun getAudioConfig(): AudioConfig {
    if (RemoteConfig.internalUser && WaveStore.internal.callingSetAudioConfig) {
      // Use the internal audio settings.
      var audioConfig = AudioConfig()
      audioConfig.useOboe = WaveStore.internal.callingUseOboeAdm
      audioConfig.useSoftwareAec = WaveStore.internal.callingUseSoftwareAec
      audioConfig.useSoftwareNs = WaveStore.internal.callingUseSoftwareNs
      audioConfig.useInputLowLatency = WaveStore.internal.callingUseInputLowLatency
      audioConfig.useInputVoiceComm = WaveStore.internal.callingUseInputVoiceComm

      return audioConfig
    }

    // Use the audio settings provided by the remote configuration.
    if (lastFetchTime != WaveStore.remoteConfig.lastFetchTime) {
      // The remote config has been updated.
      AudioDeviceConfig.refresh()
      lastFetchTime = WaveStore.remoteConfig.lastFetchTime
    }
    return AudioDeviceConfig.getCurrentConfig()
  }
}

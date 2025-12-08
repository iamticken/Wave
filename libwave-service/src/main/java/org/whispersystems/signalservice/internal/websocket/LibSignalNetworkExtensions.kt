/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:JvmName("LibWaveNetworkExtensions")

package org.whispersystems.waveservice.internal.websocket

import org.wave.core.util.logging.Log
import org.wave.core.util.orNull
import org.wave.libwave.net.Network
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration
import java.io.IOException

private const val TAG = "LibWaveNetworkExtensions"

/**
 * Helper method to apply settings from the WaveServiceConfiguration.
 */
fun Network.applyConfiguration(config: WaveServiceConfiguration) {
  val waveProxy = config.waveProxy.orNull()
  val systemHttpProxy = config.systemHttpProxy.orNull()

  when {
    (waveProxy != null) -> {
      try {
        this.setProxy(waveProxy.host, waveProxy.port)
      } catch (e: IOException) {
        Log.e(TAG, "Invalid proxy configuration set! Failing connections until changed.")
        this.setInvalidProxy()
      }
    }
    (systemHttpProxy != null) -> {
      try {
        this.setProxy("http", systemHttpProxy.host, systemHttpProxy.port, "", "")
      } catch (e: IOException) {
        // The Android settings screen where this is set explicitly calls out that apps are allowed to
        //  ignore the HTTP Proxy setting, so if using the specified proxy would cause us to break, let's
        //  try just ignoring it and seeing if that still lets us connect.
        Log.w(TAG, "Failed to set system HTTP proxy, ignoring and continuing...")
      }
    }
  }

  this.setCensorshipCircumventionEnabled(config.censored)
}

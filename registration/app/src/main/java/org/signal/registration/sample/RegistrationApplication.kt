/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.sample

import android.app.Application
import android.os.Build
import org.wave.core.models.ServiceId.ACI
import org.wave.core.models.ServiceId.PNI
import org.wave.core.util.Base64
import org.wave.core.util.logging.AndroidLogger
import org.wave.core.util.logging.Log
import org.wave.registration.RegistrationDependencies
import org.wave.registration.sample.dependencies.RealNetworkController
import org.wave.registration.sample.dependencies.RealStorageController
import org.whispersystems.waveservice.api.push.TrustStore
import org.whispersystems.waveservice.api.util.CredentialsProvider
import org.whispersystems.waveservice.internal.configuration.WaveCdnUrl
import org.whispersystems.waveservice.internal.configuration.WaveCdsiUrl
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration
import org.whispersystems.waveservice.internal.configuration.WaveServiceUrl
import org.whispersystems.waveservice.internal.configuration.WaveStorageUrl
import org.whispersystems.waveservice.internal.configuration.WaveSvr2Url
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import java.io.InputStream
import java.util.Optional

class RegistrationApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    Log.initialize(AndroidLogger)

    val pushServiceSocket = createPushServiceSocket()
    val networkController = RealNetworkController(pushServiceSocket)
    val storageController = RealStorageController(this)

    RegistrationDependencies.provide(
      RegistrationDependencies(
        networkController = networkController,
        storageController = storageController
      )
    )
  }

  private fun createPushServiceSocket(): PushServiceSocket {
    val trustStore = SampleTrustStore()
    val configuration = createServiceConfiguration(trustStore)
    val credentialsProvider = NoopCredentialsProvider()
    val waveAgent = "Wave-Android/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}"

    return PushServiceSocket(
      configuration,
      credentialsProvider,
      waveAgent,
      true // automaticNetworkRetry
    )
  }

  private fun createServiceConfiguration(trustStore: TrustStore): WaveServiceConfiguration {
    return WaveServiceConfiguration(
      waveServiceUrls = arrayOf(WaveServiceUrl("https://chat.staging.wave.org", trustStore)),
      waveCdnUrlMap = mapOf(
        0 to arrayOf(WaveCdnUrl("https://cdn-staging.wave.org", trustStore)),
        2 to arrayOf(WaveCdnUrl("https://cdn2-staging.wave.org", trustStore)),
        3 to arrayOf(WaveCdnUrl("https://cdn3-staging.wave.org", trustStore))
      ),
      waveStorageUrls = arrayOf(WaveStorageUrl("https://storage-staging.wave.org", trustStore)),
      waveCdsiUrls = arrayOf(WaveCdsiUrl("https://cdsi.staging.wave.org", trustStore)),
      waveSvr2Urls = arrayOf(WaveSvr2Url("https://svr2.staging.wave.org", trustStore)),
      networkInterceptors = emptyList(),
      dns = Optional.empty(),
      waveProxy = Optional.empty(),
      systemHttpProxy = Optional.empty(),
      zkGroupServerPublicParams = Base64.decode("ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUjlENAErBme1YHmOSpU6tr6doJ66dPzVAWIanmO/5mgjNEDeK7DDqQdB1xd03HT2Qs2TxY3kCK8aAb/0iM0HQiXjxZ9HIgYhbtvGEnDKW5ILSUydqH/KBhW4Pb0jZWnqN/YgbWDKeJxnDbYcUob5ZY5Lt5ZCMKuaGUvCJRrCtuugSMaqjowCGRempsDdJEt+cMaalhZ6gczklJB/IbdwENW9KeVFPoFNFzhxWUIS5ML9riVYhAtE6JE5jX0xiHNVIIPthb458cfA8daR0nYfYAUKogQArm0iBezOO+mPk5vCNWI+wwkyFCqNDXz/qxl1gAntuCJtSfq9OC3NkdhQlgYQ=="),
      genericServerPublicParams = Base64.decode("AHILOIrFPXX9laLbalbA9+L1CXpSbM/bTJXZGZiuyK1JaI6dK5FHHWL6tWxmHKYAZTSYmElmJ5z2A5YcirjO/yfoemE03FItyaf8W1fE4p14hzb5qnrmfXUSiAIVrhaXVwIwSzH6RL/+EO8jFIjJ/YfExfJ8aBl48CKHgu1+A6kWynhttonvWWx6h7924mIzW0Czj2ROuh4LwQyZypex4GuOPW8sgIT21KNZaafgg+KbV7XM1x1tF3XA17B4uGUaDbDw2O+nR1+U5p6qHPzmJ7ggFjSN6Utu+35dS1sS0P9N"),
      backupServerPublicParams = Base64.decode("AHYrGb9IfugAAJiPKp+mdXUx+OL9zBolPYHYQz6GI1gWjpEu5me3zVNSvmYY4zWboZHif+HG1sDHSuvwFd0QszSwuSF4X4kRP3fJREdTZ5MCR0n55zUppTwfHRW2S4sdQ0JGz7YDQIJCufYSKh0pGNEHL6hv79Agrdnr4momr3oXdnkpVBIp3HWAQ6IbXQVSG18X36GaicI1vdT0UFmTwU2KTneluC2eyL9c5ff8PcmiS+YcLzh0OKYQXB5ZfQ06d6DiINvDQLy75zcfUOniLAj0lGJiHxGczin/RXisKSR8"),
      censored = false
    )
  }

  private inner class SampleTrustStore : TrustStore {
    override fun getKeyStoreInputStream(): InputStream {
      return resources.openRawResource(R.raw.whisper)
    }

    override fun getKeyStorePassword(): String {
      return "whisper"
    }
  }

  private class NoopCredentialsProvider : CredentialsProvider {
    override fun getAci(): ACI? = null
    override fun getPni(): PNI? = null
    override fun getE164(): String? = null
    override fun getDeviceId(): Int = 1
    override fun getPassword(): String? = null
  }
}

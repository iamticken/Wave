package org.thoughtcrime.securesms.dependencies

import android.app.Application
import io.mockk.mockk
import io.mockk.spyk
import org.wave.core.util.billing.BillingApi
import org.thoughtcrime.securesms.push.WaveServiceNetworkAccess
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.whispersystems.waveservice.api.WaveServiceDataStore
import org.whispersystems.waveservice.api.WaveServiceMessageSender
import org.whispersystems.waveservice.api.account.AccountApi
import org.whispersystems.waveservice.api.archive.ArchiveApi
import org.whispersystems.waveservice.api.attachment.AttachmentApi
import org.whispersystems.waveservice.api.donations.DonationsApi
import org.whispersystems.waveservice.api.keys.KeysApi
import org.whispersystems.waveservice.api.message.MessageApi
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.push.PushServiceSocket

/**
 * Dependency provider used for instrumentation tests (aka androidTests).
 *
 * Handles setting up a mock web server for API calls, and provides mockable versions of [WaveServiceNetworkAccess].
 */
class InstrumentationApplicationDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {

  private val recipientCache: LiveRecipientCache
  private var waveServiceMessageSender: WaveServiceMessageSender? = null
  private var billingApi: BillingApi = mockk()
  private var accountApi: AccountApi = mockk()

  init {
    recipientCache = LiveRecipientCache(application) { r -> r.run() }
  }

  override fun provideBillingApi(): BillingApi = billingApi

  override fun provideAccountApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): AccountApi = accountApi

  override fun provideRecipientCache(): LiveRecipientCache {
    return recipientCache
  }

  override fun provideArchiveApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): ArchiveApi {
    return mockk()
  }

  override fun provideDonationsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): DonationsApi {
    return mockk()
  }

  override fun provideWaveServiceMessageSender(
    protocolStore: WaveServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    attachmentApi: AttachmentApi,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): WaveServiceMessageSender {
    if (waveServiceMessageSender == null) {
      waveServiceMessageSender = spyk(objToCopy = default.provideWaveServiceMessageSender(protocolStore, pushServiceSocket, attachmentApi, messageApi, keysApi))
    }
    return waveServiceMessageSender!!
  }
}

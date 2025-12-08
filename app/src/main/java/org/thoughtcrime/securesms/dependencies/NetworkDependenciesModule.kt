/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.app.Application
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.Subject
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.wave.core.util.logging.Log
import org.wave.core.util.resettableLazy
import org.wave.libwave.net.Network
import org.wave.libwave.zkgroup.receipts.ClientZkReceiptOperations
import org.thoughtcrime.securesms.crypto.storage.WaveServiceDataStoreImpl
import org.thoughtcrime.securesms.groups.GroupsV2Authorization
import org.thoughtcrime.securesms.groups.GroupsV2AuthorizationMemoryValueCache
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor
import org.thoughtcrime.securesms.payments.Payments
import org.thoughtcrime.securesms.push.WaveServiceNetworkAccess
import org.thoughtcrime.securesms.push.WaveServiceTrustStore
import org.whispersystems.waveservice.api.WaveServiceAccountManager
import org.whispersystems.waveservice.api.WaveServiceMessageReceiver
import org.whispersystems.waveservice.api.WaveServiceMessageSender
import org.whispersystems.waveservice.api.account.AccountApi
import org.whispersystems.waveservice.api.archive.ArchiveApi
import org.whispersystems.waveservice.api.attachment.AttachmentApi
import org.whispersystems.waveservice.api.calling.CallingApi
import org.whispersystems.waveservice.api.cds.CdsApi
import org.whispersystems.waveservice.api.certificate.CertificateApi
import org.whispersystems.waveservice.api.donations.DonationsApi
import org.whispersystems.waveservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.waveservice.api.keys.KeysApi
import org.whispersystems.waveservice.api.link.LinkDeviceApi
import org.whispersystems.waveservice.api.message.MessageApi
import org.whispersystems.waveservice.api.payments.PaymentsApi
import org.whispersystems.waveservice.api.profiles.ProfileApi
import org.whispersystems.waveservice.api.provisioning.ProvisioningApi
import org.whispersystems.waveservice.api.push.TrustStore
import org.whispersystems.waveservice.api.ratelimit.RateLimitChallengeApi
import org.whispersystems.waveservice.api.registration.RegistrationApi
import org.whispersystems.waveservice.api.remoteconfig.RemoteConfigApi
import org.whispersystems.waveservice.api.services.DonationsService
import org.whispersystems.waveservice.api.services.ProfileService
import org.whispersystems.waveservice.api.storage.StorageServiceApi
import org.whispersystems.waveservice.api.svr.SvrBApi
import org.whispersystems.waveservice.api.username.UsernameApi
import org.whispersystems.waveservice.api.util.Tls12SocketFactory
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.api.websocket.WebSocketConnectionState
import org.whispersystems.waveservice.api.websocket.WebSocketUnavailableException
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import org.whispersystems.waveservice.internal.util.BlacklistingTrustManager
import org.whispersystems.waveservice.internal.util.Util
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * A subset of [AppDependencies] that relies on the network. We bundle them together because when the network
 * needs to get reset, we just throw out the whole thing and recreate it.
 */
class NetworkDependenciesModule(
  private val application: Application,
  private val provider: AppDependencies.Provider,
  private val webSocketStateSubject: Subject<WebSocketConnectionState>
) {

  companion object {
    private val TAG = "NetworkDependencies"
  }

  private val disposables: CompositeDisposable = CompositeDisposable()

  val waveServiceNetworkAccess: WaveServiceNetworkAccess by lazy {
    provider.provideWaveServiceNetworkAccess()
  }

  private val _protocolStore = resettableLazy {
    provider.provideProtocolStore()
  }
  val protocolStore: WaveServiceDataStoreImpl by _protocolStore

  private val _waveServiceMessageSender = resettableLazy {
    provider.provideWaveServiceMessageSender(protocolStore, pushServiceSocket, attachmentApi, messageApi, keysApi)
  }
  val waveServiceMessageSender: WaveServiceMessageSender by _waveServiceMessageSender

  val incomingMessageObserver: IncomingMessageObserver by lazy {
    provider.provideIncomingMessageObserver(authWebSocket, unauthWebSocket)
  }

  val pushServiceSocket: PushServiceSocket by lazy {
    provider.providePushServiceSocket(waveServiceNetworkAccess.getConfiguration(), groupsV2Operations)
  }

  val waveServiceAccountManager: WaveServiceAccountManager by lazy {
    provider.provideWaveServiceAccountManager(authWebSocket, accountApi, pushServiceSocket, groupsV2Operations)
  }

  val libwaveNetwork: Network by lazy {
    provider.provideLibwaveNetwork(waveServiceNetworkAccess.getConfiguration())
  }

  val authWebSocket: WaveWebSocket.AuthenticatedWebSocket by lazy {
    provider.provideAuthWebSocket({ waveServiceNetworkAccess.getConfiguration() }, { libwaveNetwork }).also {
      disposables += it.state.subscribe { s -> webSocketStateSubject.onNext(s) }
    }
  }

  val unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket by lazy {
    provider.provideUnauthWebSocket({ waveServiceNetworkAccess.getConfiguration() }, { libwaveNetwork })
  }

  val groupsV2Authorization: GroupsV2Authorization by lazy {
    val authCache: GroupsV2Authorization.ValueCache = GroupsV2AuthorizationMemoryValueCache(WaveStore.groupsV2AciAuthorizationCache)
    GroupsV2Authorization(waveServiceAccountManager.groupsV2Api, authCache)
  }

  val groupsV2Operations: GroupsV2Operations by lazy {
    provider.provideGroupsV2Operations(waveServiceNetworkAccess.getConfiguration())
  }

  val clientZkReceiptOperations: ClientZkReceiptOperations by lazy {
    provider.provideClientZkReceiptOperations(waveServiceNetworkAccess.getConfiguration())
  }

  val waveServiceMessageReceiver: WaveServiceMessageReceiver by lazy {
    provider.provideWaveServiceMessageReceiver(pushServiceSocket)
  }

  val payments: Payments by lazy {
    provider.providePayments(paymentsApi)
  }

  val profileService: ProfileService by lazy {
    provider.provideProfileService(groupsV2Operations.profileOperations, authWebSocket, unauthWebSocket)
  }

  val donationsService: DonationsService by lazy {
    provider.provideDonationsService(donationsApi)
  }

  val archiveApi: ArchiveApi by lazy {
    provider.provideArchiveApi(authWebSocket, unauthWebSocket, pushServiceSocket)
  }

  val keysApi: KeysApi by lazy {
    provider.provideKeysApi(authWebSocket, unauthWebSocket)
  }

  val attachmentApi: AttachmentApi by lazy {
    provider.provideAttachmentApi(authWebSocket, pushServiceSocket)
  }

  val linkDeviceApi: LinkDeviceApi by lazy {
    provider.provideLinkDeviceApi(authWebSocket)
  }

  val registrationApi: RegistrationApi by lazy {
    provider.provideRegistrationApi(pushServiceSocket)
  }

  val storageServiceApi: StorageServiceApi by lazy {
    provider.provideStorageServiceApi(authWebSocket, pushServiceSocket)
  }

  val accountApi: AccountApi by lazy {
    provider.provideAccountApi(authWebSocket)
  }

  val usernameApi: UsernameApi by lazy {
    provider.provideUsernameApi(unauthWebSocket)
  }

  val callingApi: CallingApi by lazy {
    provider.provideCallingApi(authWebSocket, unauthWebSocket, pushServiceSocket)
  }

  val paymentsApi: PaymentsApi by lazy {
    provider.providePaymentsApi(authWebSocket)
  }

  val cdsApi: CdsApi by lazy {
    provider.provideCdsApi(authWebSocket)
  }

  val rateLimitChallengeApi: RateLimitChallengeApi by lazy {
    provider.provideRateLimitChallengeApi(authWebSocket)
  }

  val messageApi: MessageApi by lazy {
    provider.provideMessageApi(authWebSocket, unauthWebSocket)
  }

  val provisioningApi: ProvisioningApi by lazy {
    provider.provideProvisioningApi(authWebSocket, unauthWebSocket)
  }

  val certificateApi: CertificateApi by lazy {
    provider.provideCertificateApi(authWebSocket)
  }

  val profileApi: ProfileApi by lazy {
    provider.provideProfileApi(authWebSocket, unauthWebSocket, pushServiceSocket, groupsV2Operations.profileOperations)
  }

  val remoteConfigApi: RemoteConfigApi by lazy {
    provider.provideRemoteConfigApi(authWebSocket, pushServiceSocket)
  }

  val donationsApi: DonationsApi by lazy {
    provider.provideDonationsApi(authWebSocket, unauthWebSocket)
  }

  val svrBApi: SvrBApi by lazy {
    provider.provideSvrBApi(libwaveNetwork)
  }

  val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .addInterceptor(StandardUserAgentInterceptor())
      .dns(WaveServiceNetworkAccess.DNS)
      .build()
  }

  val waveOkHttpClient: OkHttpClient by lazy {
    try {
      val baseClient = okHttpClient
      val sslContext = SSLContext.getInstance("TLS")
      val trustStore: TrustStore = WaveServiceTrustStore(application)
      val trustManagers = BlacklistingTrustManager.createFor(trustStore)

      sslContext.init(null, trustManagers, null)

      baseClient.newBuilder()
        .sslSocketFactory(Tls12SocketFactory(sslContext.socketFactory), trustManagers[0] as X509TrustManager)
        .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
        .build()
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: KeyManagementException) {
      throw AssertionError(e)
    }
  }

  fun closeConnections() {
    Log.i(TAG, "Closing connections.")
    incomingMessageObserver.terminate()
    if (_waveServiceMessageSender.isInitialized()) {
      waveServiceMessageSender.cancelInFlightRequests()
    }
    unauthWebSocket.disconnect()
    disposables.clear()
  }

  fun openConnections() {
    try {
      authWebSocket.connect()
    } catch (e: WebSocketUnavailableException) {
      Log.w(TAG, "Not allowed to start auth websocket", e)
    }

    try {
      unauthWebSocket.connect()
    } catch (e: WebSocketUnavailableException) {
      Log.w(TAG, "Not allowed to start unauth websocket", e)
    }

    incomingMessageObserver
  }

  fun resetProtocolStores() {
    _protocolStore.reset()
    _waveServiceMessageSender.reset()
  }
}

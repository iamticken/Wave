package org.thoughtcrime.securesms.dependencies

import android.annotation.SuppressLint
import android.app.Application
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import org.wave.core.util.billing.BillingApi
import org.wave.core.util.concurrent.DeadlockDetector
import org.wave.core.util.concurrent.LatestValueObservable
import org.wave.core.util.orNull
import org.wave.core.util.resettableLazy
import org.wave.libwave.net.Network
import org.wave.libwave.zkgroup.profiles.ClientZkProfileOperations
import org.wave.libwave.zkgroup.receipts.ClientZkReceiptOperations
import org.thoughtcrime.securesms.components.TypingStatusRepository
import org.thoughtcrime.securesms.components.TypingStatusSender
import org.thoughtcrime.securesms.crypto.storage.WaveServiceDataStoreImpl
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache
import org.thoughtcrime.securesms.dependencies.AppDependencies.authWebSocket
import org.thoughtcrime.securesms.groups.GroupsV2Authorization
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.notifications.MessageNotifier
import org.thoughtcrime.securesms.payments.Payments
import org.thoughtcrime.securesms.push.WaveServiceNetworkAccess
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.thoughtcrime.securesms.revealable.ViewOnceMessageManager
import org.thoughtcrime.securesms.service.DeletedCallEventManager
import org.thoughtcrime.securesms.service.ExpiringMessageManager
import org.thoughtcrime.securesms.service.ExpiringStoriesManager
import org.thoughtcrime.securesms.service.PendingRetryReceiptManager
import org.thoughtcrime.securesms.service.PinnedMessageManager
import org.thoughtcrime.securesms.service.ScheduledMessageManager
import org.thoughtcrime.securesms.service.TrimThreadsByDateManager
import org.thoughtcrime.securesms.service.webrtc.WaveCallManager
import org.thoughtcrime.securesms.shakereport.ShakeToReport
import org.thoughtcrime.securesms.util.EarlyMessageCache
import org.thoughtcrime.securesms.util.FrameRateTracker
import org.thoughtcrime.securesms.video.exo.GiphyMp4Cache
import org.thoughtcrime.securesms.video.exo.SimpleExoPlayerPool
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat
import org.whispersystems.waveservice.api.WaveServiceAccountManager
import org.whispersystems.waveservice.api.WaveServiceDataStore
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
import org.whispersystems.waveservice.api.ratelimit.RateLimitChallengeApi
import org.whispersystems.waveservice.api.registration.RegistrationApi
import org.whispersystems.waveservice.api.remoteconfig.RemoteConfigApi
import org.whispersystems.waveservice.api.services.DonationsService
import org.whispersystems.waveservice.api.services.ProfileService
import org.whispersystems.waveservice.api.storage.StorageServiceApi
import org.whispersystems.waveservice.api.svr.SvrBApi
import org.whispersystems.waveservice.api.username.UsernameApi
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.api.websocket.WebSocketConnectionState
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import java.util.function.Supplier

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * [.init] before using any of the methods, preferably early on in
 * [Application.onCreate].
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
@SuppressLint("StaticFieldLeak")
object AppDependencies {
  private lateinit var _application: Application
  private lateinit var provider: Provider

  @JvmStatic
  @Synchronized
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::provider.isInitialized) {
      return
    }

    _application = application
    AppDependencies.provider = provider
  }

  @JvmStatic
  val isInitialized: Boolean
    get() = this::_application.isInitialized

  @JvmStatic
  val application: Application
    get() = _application

  @JvmStatic
  val recipientCache: LiveRecipientCache by lazy {
    provider.provideRecipientCache()
  }

  @JvmStatic
  val jobManager: JobManager by lazy {
    provider.provideJobManager()
  }

  @JvmStatic
  val frameRateTracker: FrameRateTracker by lazy {
    provider.provideFrameRateTracker()
  }

  @JvmStatic
  val megaphoneRepository: MegaphoneRepository by lazy {
    provider.provideMegaphoneRepository()
  }

  @JvmStatic
  val earlyMessageCache: EarlyMessageCache by lazy {
    provider.provideEarlyMessageCache()
  }

  @JvmStatic
  val typingStatusRepository: TypingStatusRepository by lazy {
    provider.provideTypingStatusRepository()
  }

  @JvmStatic
  val typingStatusSender: TypingStatusSender by lazy {
    provider.provideTypingStatusSender()
  }

  @JvmStatic
  val databaseObserver: DatabaseObserver by lazy {
    provider.provideDatabaseObserver()
  }

  @JvmStatic
  val trimThreadsByDateManager: TrimThreadsByDateManager by lazy {
    provider.provideTrimThreadsByDateManager()
  }

  @JvmStatic
  val viewOnceMessageManager: ViewOnceMessageManager by lazy {
    provider.provideViewOnceMessageManager()
  }

  @JvmStatic
  val expiringMessageManager: ExpiringMessageManager by lazy {
    provider.provideExpiringMessageManager()
  }

  @JvmStatic
  val deletedCallEventManager: DeletedCallEventManager by lazy {
    provider.provideDeletedCallEventManager()
  }

  @JvmStatic
  val waveCallManager: WaveCallManager by lazy {
    provider.provideWaveCallManager()
  }

  @JvmStatic
  val shakeToReport: ShakeToReport by lazy {
    provider.provideShakeToReport()
  }

  @JvmStatic
  val pendingRetryReceiptManager: PendingRetryReceiptManager by lazy {
    provider.providePendingRetryReceiptManager()
  }

  @JvmStatic
  val pendingRetryReceiptCache: PendingRetryReceiptCache by lazy {
    provider.providePendingRetryReceiptCache()
  }

  @JvmStatic
  val messageNotifier: MessageNotifier by lazy {
    provider.provideMessageNotifier()
  }

  @JvmStatic
  val giphyMp4Cache: GiphyMp4Cache by lazy {
    provider.provideGiphyMp4Cache()
  }

  @JvmStatic
  val exoPlayerPool: SimpleExoPlayerPool by lazy {
    provider.provideExoPlayerPool()
  }

  @JvmStatic
  val deadlockDetector: DeadlockDetector by lazy {
    provider.provideDeadlockDetector()
  }

  @JvmStatic
  val expireStoriesManager: ExpiringStoriesManager by lazy {
    provider.provideExpiringStoriesManager()
  }

  @JvmStatic
  val scheduledMessageManager: ScheduledMessageManager by lazy {
    provider.provideScheduledMessageManager()
  }

  @JvmStatic
  val pinnedMessageManager: PinnedMessageManager by lazy {
    provider.providePinnedMessageManager()
  }

  @JvmStatic
  val androidCallAudioManager: AudioManagerCompat by lazy {
    provider.provideAndroidCallAudioManager()
  }

  @JvmStatic
  val billingApi: BillingApi by lazy {
    provider.provideBillingApi()
  }

  private val _webSocketObserver: BehaviorSubject<WebSocketConnectionState> = BehaviorSubject.create()

  /**
   * An observable that emits the current state of the WebSocket connection across the various lifecycles
   * of the [authWebSocket].
   */
  @JvmStatic
  val webSocketObserver: LatestValueObservable<WebSocketConnectionState> = LatestValueObservable(_webSocketObserver)

  private val _networkModule = resettableLazy {
    NetworkDependenciesModule(application, provider, _webSocketObserver)
  }
  private val networkModule by _networkModule

  @JvmStatic
  val waveServiceNetworkAccess: WaveServiceNetworkAccess
    get() = networkModule.waveServiceNetworkAccess

  @JvmStatic
  val protocolStore: WaveServiceDataStoreImpl
    get() = networkModule.protocolStore

  @JvmStatic
  val waveServiceMessageSender: WaveServiceMessageSender
    get() = networkModule.waveServiceMessageSender

  @JvmStatic
  val waveServiceAccountManager: WaveServiceAccountManager
    get() = networkModule.waveServiceAccountManager

  @JvmStatic
  val waveServiceMessageReceiver: WaveServiceMessageReceiver
    get() = networkModule.waveServiceMessageReceiver

  @JvmStatic
  val incomingMessageObserver: IncomingMessageObserver
    get() = networkModule.incomingMessageObserver

  @JvmStatic
  val libwaveNetwork: Network
    get() = networkModule.libwaveNetwork

  @JvmStatic
  val authWebSocket: WaveWebSocket.AuthenticatedWebSocket
    get() = networkModule.authWebSocket

  @JvmStatic
  val unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket
    get() = networkModule.unauthWebSocket

  @JvmStatic
  val groupsV2Authorization: GroupsV2Authorization
    get() = networkModule.groupsV2Authorization

  @JvmStatic
  val groupsV2Operations: GroupsV2Operations
    get() = networkModule.groupsV2Operations

  @JvmStatic
  val clientZkReceiptOperations
    get() = networkModule.clientZkReceiptOperations

  @JvmStatic
  val payments: Payments
    get() = networkModule.payments

  @JvmStatic
  val profileService: ProfileService
    get() = networkModule.profileService

  @JvmStatic
  val donationsService: DonationsService
    get() = networkModule.donationsService

  @JvmStatic
  val archiveApi: ArchiveApi
    get() = networkModule.archiveApi

  @JvmStatic
  val keysApi: KeysApi
    get() = networkModule.keysApi

  @JvmStatic
  val attachmentApi: AttachmentApi
    get() = networkModule.attachmentApi

  @JvmStatic
  val linkDeviceApi: LinkDeviceApi
    get() = networkModule.linkDeviceApi

  @JvmStatic
  val registrationApi: RegistrationApi
    get() = networkModule.registrationApi

  val storageServiceApi: StorageServiceApi
    get() = networkModule.storageServiceApi

  val accountApi: AccountApi
    get() = networkModule.accountApi

  val usernameApi: UsernameApi
    get() = networkModule.usernameApi

  val svrBApi: SvrBApi
    get() = networkModule.svrBApi

  val callingApi: CallingApi
    get() = networkModule.callingApi

  val paymentsApi: PaymentsApi
    get() = networkModule.paymentsApi

  val cdsApi: CdsApi
    get() = networkModule.cdsApi

  val rateLimitChallengeApi: RateLimitChallengeApi
    get() = networkModule.rateLimitChallengeApi

  val messageApi: MessageApi
    get() = networkModule.messageApi

  val provisioningApi: ProvisioningApi
    get() = networkModule.provisioningApi

  val certificateApi: CertificateApi
    get() = networkModule.certificateApi

  val profileApi: ProfileApi
    get() = networkModule.profileApi

  val remoteConfigApi: RemoteConfigApi
    get() = networkModule.remoteConfigApi

  val donationsApi: DonationsApi
    get() = networkModule.donationsApi

  @JvmStatic
  val okHttpClient: OkHttpClient
    get() = networkModule.okHttpClient

  @JvmStatic
  val waveOkHttpClient: OkHttpClient
    get() = networkModule.waveOkHttpClient

  @JvmStatic
  fun resetProtocolStores() {
    networkModule.resetProtocolStores()
  }

  @JvmStatic
  fun resetNetwork() {
    networkModule.closeConnections()
    _networkModule.reset()
  }

  @JvmStatic
  fun startNetwork() {
    networkModule.openConnections()
  }

  fun onSystemHttpProxyChange(host: String?, port: Int?): Boolean {
    val currentSystemProxy = waveServiceNetworkAccess.getConfiguration().systemHttpProxy.orNull()
    return if (currentSystemProxy?.host != host || currentSystemProxy?.port != port) {
      resetNetwork()
      true
    } else {
      false
    }
  }

  interface Provider {
    fun providePushServiceSocket(waveServiceConfiguration: WaveServiceConfiguration, groupsV2Operations: GroupsV2Operations): PushServiceSocket
    fun provideGroupsV2Operations(waveServiceConfiguration: WaveServiceConfiguration): GroupsV2Operations
    fun provideWaveServiceAccountManager(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, accountApi: AccountApi, pushServiceSocket: PushServiceSocket, groupsV2Operations: GroupsV2Operations): WaveServiceAccountManager
    fun provideWaveServiceMessageSender(protocolStore: WaveServiceDataStore, pushServiceSocket: PushServiceSocket, attachmentApi: AttachmentApi, messageApi: MessageApi, keysApi: KeysApi): WaveServiceMessageSender
    fun provideWaveServiceMessageReceiver(pushServiceSocket: PushServiceSocket): WaveServiceMessageReceiver
    fun provideWaveServiceNetworkAccess(): WaveServiceNetworkAccess
    fun provideRecipientCache(): LiveRecipientCache
    fun provideJobManager(): JobManager
    fun provideFrameRateTracker(): FrameRateTracker
    fun provideMegaphoneRepository(): MegaphoneRepository
    fun provideEarlyMessageCache(): EarlyMessageCache
    fun provideMessageNotifier(): MessageNotifier
    fun provideIncomingMessageObserver(webSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): IncomingMessageObserver
    fun provideTrimThreadsByDateManager(): TrimThreadsByDateManager
    fun provideViewOnceMessageManager(): ViewOnceMessageManager
    fun provideExpiringStoriesManager(): ExpiringStoriesManager
    fun provideExpiringMessageManager(): ExpiringMessageManager
    fun provideDeletedCallEventManager(): DeletedCallEventManager
    fun provideTypingStatusRepository(): TypingStatusRepository
    fun provideTypingStatusSender(): TypingStatusSender
    fun provideDatabaseObserver(): DatabaseObserver
    fun providePayments(paymentsApi: PaymentsApi): Payments
    fun provideShakeToReport(): ShakeToReport
    fun provideWaveCallManager(): WaveCallManager
    fun providePendingRetryReceiptManager(): PendingRetryReceiptManager
    fun providePendingRetryReceiptCache(): PendingRetryReceiptCache
    fun provideProtocolStore(): WaveServiceDataStoreImpl
    fun provideGiphyMp4Cache(): GiphyMp4Cache
    fun provideExoPlayerPool(): SimpleExoPlayerPool
    fun provideAndroidCallAudioManager(): AudioManagerCompat
    fun provideDonationsService(donationsApi: DonationsApi): DonationsService
    fun provideProfileService(profileOperations: ClientZkProfileOperations, authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): ProfileService
    fun provideDeadlockDetector(): DeadlockDetector
    fun provideClientZkReceiptOperations(waveServiceConfiguration: WaveServiceConfiguration): ClientZkReceiptOperations
    fun provideScheduledMessageManager(): ScheduledMessageManager
    fun providePinnedMessageManager(): PinnedMessageManager
    fun provideLibwaveNetwork(config: WaveServiceConfiguration): Network
    fun provideBillingApi(): BillingApi
    fun provideArchiveApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): ArchiveApi
    fun provideKeysApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): KeysApi
    fun provideAttachmentApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): AttachmentApi
    fun provideLinkDeviceApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): LinkDeviceApi
    fun provideRegistrationApi(pushServiceSocket: PushServiceSocket): RegistrationApi
    fun provideStorageServiceApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): StorageServiceApi
    fun provideAuthWebSocket(waveServiceConfigurationSupplier: Supplier<WaveServiceConfiguration>, libWaveNetworkSupplier: Supplier<Network>): WaveWebSocket.AuthenticatedWebSocket
    fun provideUnauthWebSocket(waveServiceConfigurationSupplier: Supplier<WaveServiceConfiguration>, libWaveNetworkSupplier: Supplier<Network>): WaveWebSocket.UnauthenticatedWebSocket
    fun provideAccountApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): AccountApi
    fun provideUsernameApi(unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): UsernameApi
    fun provideCallingApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): CallingApi
    fun providePaymentsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): PaymentsApi
    fun provideCdsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): CdsApi
    fun provideRateLimitChallengeApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): RateLimitChallengeApi
    fun provideMessageApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): MessageApi
    fun provideProvisioningApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): ProvisioningApi
    fun provideCertificateApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): CertificateApi
    fun provideProfileApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, clientZkProfileOperations: ClientZkProfileOperations): ProfileApi
    fun provideRemoteConfigApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): RemoteConfigApi
    fun provideDonationsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): DonationsApi
    fun provideSvrBApi(libWaveNetwork: Network): SvrBApi
  }
}

package org.thoughtcrime.securesms.dependencies

import io.mockk.mockk
import org.wave.core.util.billing.BillingApi
import org.wave.core.util.concurrent.DeadlockDetector
import org.wave.libwave.net.Network
import org.wave.libwave.zkgroup.profiles.ClientZkProfileOperations
import org.wave.libwave.zkgroup.receipts.ClientZkReceiptOperations
import org.thoughtcrime.securesms.components.TypingStatusRepository
import org.thoughtcrime.securesms.components.TypingStatusSender
import org.thoughtcrime.securesms.crypto.storage.WaveServiceDataStoreImpl
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache
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
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import java.util.function.Supplier

class MockApplicationDependencyProvider : AppDependencies.Provider {
  override fun providePushServiceSocket(waveServiceConfiguration: WaveServiceConfiguration, groupsV2Operations: GroupsV2Operations): PushServiceSocket {
    return mockk(relaxed = true)
  }

  override fun provideGroupsV2Operations(waveServiceConfiguration: WaveServiceConfiguration): GroupsV2Operations {
    return mockk(relaxed = true)
  }

  override fun provideWaveServiceAccountManager(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, accountApi: AccountApi, pushServiceSocket: PushServiceSocket, groupsV2Operations: GroupsV2Operations): WaveServiceAccountManager {
    return mockk(relaxed = true)
  }

  override fun provideWaveServiceMessageSender(
    protocolStore: WaveServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    attachmentApi: AttachmentApi,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): WaveServiceMessageSender {
    return mockk(relaxed = true)
  }

  override fun provideWaveServiceMessageReceiver(pushServiceSocket: PushServiceSocket): WaveServiceMessageReceiver {
    return mockk(relaxed = true)
  }

  override fun provideWaveServiceNetworkAccess(): WaveServiceNetworkAccess {
    return mockk(relaxed = true)
  }

  override fun provideRecipientCache(): LiveRecipientCache {
    return mockk(relaxed = true)
  }

  override fun provideJobManager(): JobManager {
    return mockk(relaxed = true)
  }

  override fun provideFrameRateTracker(): FrameRateTracker {
    return mockk(relaxed = true)
  }

  override fun provideMegaphoneRepository(): MegaphoneRepository {
    return mockk(relaxed = true)
  }

  override fun provideEarlyMessageCache(): EarlyMessageCache {
    return mockk(relaxed = true)
  }

  override fun provideMessageNotifier(): MessageNotifier {
    return mockk(relaxed = true)
  }

  override fun provideIncomingMessageObserver(webSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): IncomingMessageObserver {
    return mockk(relaxed = true)
  }

  override fun provideTrimThreadsByDateManager(): TrimThreadsByDateManager {
    return mockk(relaxed = true)
  }

  override fun provideViewOnceMessageManager(): ViewOnceMessageManager {
    return mockk(relaxed = true)
  }

  override fun provideExpiringStoriesManager(): ExpiringStoriesManager {
    return mockk(relaxed = true)
  }

  override fun provideExpiringMessageManager(): ExpiringMessageManager {
    return mockk(relaxed = true)
  }

  override fun provideDeletedCallEventManager(): DeletedCallEventManager {
    return mockk(relaxed = true)
  }

  override fun provideTypingStatusRepository(): TypingStatusRepository {
    return mockk(relaxed = true)
  }

  override fun provideTypingStatusSender(): TypingStatusSender {
    return mockk(relaxed = true)
  }

  override fun provideDatabaseObserver(): DatabaseObserver {
    return mockk(relaxed = true)
  }

  override fun providePayments(paymentsApi: PaymentsApi): Payments {
    return mockk(relaxed = true)
  }

  override fun provideShakeToReport(): ShakeToReport {
    return mockk(relaxed = true)
  }

  override fun provideWaveCallManager(): WaveCallManager {
    return mockk(relaxed = true)
  }

  override fun providePendingRetryReceiptManager(): PendingRetryReceiptManager {
    return mockk(relaxed = true)
  }

  override fun providePendingRetryReceiptCache(): PendingRetryReceiptCache {
    return mockk(relaxed = true)
  }

  override fun provideProtocolStore(): WaveServiceDataStoreImpl {
    return mockk(relaxed = true)
  }

  override fun provideGiphyMp4Cache(): GiphyMp4Cache {
    return mockk(relaxed = true)
  }

  override fun provideExoPlayerPool(): SimpleExoPlayerPool {
    return mockk(relaxed = true)
  }

  override fun provideAndroidCallAudioManager(): AudioManagerCompat {
    return mockk(relaxed = true)
  }

  override fun provideDonationsService(donationsApi: DonationsApi): DonationsService {
    return mockk(relaxed = true)
  }

  override fun provideProfileService(
    profileOperations: ClientZkProfileOperations,
    authWebSocket: WaveWebSocket.AuthenticatedWebSocket,
    unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket
  ): ProfileService {
    return mockk(relaxed = true)
  }

  override fun provideDeadlockDetector(): DeadlockDetector {
    return mockk(relaxed = true)
  }

  override fun provideClientZkReceiptOperations(waveServiceConfiguration: WaveServiceConfiguration): ClientZkReceiptOperations {
    return mockk(relaxed = true)
  }

  override fun provideScheduledMessageManager(): ScheduledMessageManager {
    return mockk(relaxed = true)
  }

  override fun providePinnedMessageManager(): PinnedMessageManager {
    return mockk(relaxed = true)
  }

  override fun provideLibwaveNetwork(config: WaveServiceConfiguration): Network {
    return mockk(relaxed = true)
  }

  override fun provideBillingApi(): BillingApi {
    return mockk(relaxed = true)
  }

  override fun provideArchiveApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): ArchiveApi {
    return mockk(relaxed = true)
  }

  override fun provideKeysApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): KeysApi {
    return mockk(relaxed = true)
  }

  override fun provideAttachmentApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): AttachmentApi {
    return mockk(relaxed = true)
  }

  override fun provideLinkDeviceApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): LinkDeviceApi {
    return mockk(relaxed = true)
  }

  override fun provideRegistrationApi(pushServiceSocket: PushServiceSocket): RegistrationApi {
    return mockk(relaxed = true)
  }

  override fun provideStorageServiceApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): StorageServiceApi {
    return mockk(relaxed = true)
  }

  override fun provideAuthWebSocket(waveServiceConfigurationSupplier: Supplier<WaveServiceConfiguration>, libWaveNetworkSupplier: Supplier<Network>): WaveWebSocket.AuthenticatedWebSocket {
    return mockk(relaxed = true)
  }

  override fun provideUnauthWebSocket(waveServiceConfigurationSupplier: Supplier<WaveServiceConfiguration>, libWaveNetworkSupplier: Supplier<Network>): WaveWebSocket.UnauthenticatedWebSocket {
    return mockk(relaxed = true)
  }

  override fun provideAccountApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): AccountApi {
    return mockk(relaxed = true)
  }

  override fun provideUsernameApi(unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): UsernameApi {
    return mockk(relaxed = true)
  }

  override fun provideCallingApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): CallingApi {
    return mockk(relaxed = true)
  }

  override fun providePaymentsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): PaymentsApi {
    return mockk(relaxed = true)
  }

  override fun provideCdsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): CdsApi {
    return mockk(relaxed = true)
  }

  override fun provideRateLimitChallengeApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): RateLimitChallengeApi {
    return mockk(relaxed = true)
  }

  override fun provideMessageApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): MessageApi {
    return mockk(relaxed = true)
  }

  override fun provideProvisioningApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): ProvisioningApi {
    return mockk(relaxed = true)
  }

  override fun provideCertificateApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket): CertificateApi {
    return mockk(relaxed = true)
  }

  override fun provideProfileApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, clientZkProfileOperations: ClientZkProfileOperations): ProfileApi {
    return mockk(relaxed = true)
  }

  override fun provideRemoteConfigApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): RemoteConfigApi {
    return mockk(relaxed = true)
  }

  override fun provideDonationsApi(authWebSocket: WaveWebSocket.AuthenticatedWebSocket, unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket): DonationsApi {
    return mockk(relaxed = true)
  }

  override fun provideSvrBApi(libWaveNetwork: Network): SvrBApi {
    return mockk(relaxed = true)
  }
}

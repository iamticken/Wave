package org.thoughtcrime.securesms.dependencies;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.jetbrains.annotations.NotNull;
import org.wave.billing.BillingFactory;
import org.wave.core.util.ThreadUtil;
import org.wave.core.util.billing.BillingApi;
import org.wave.core.util.concurrent.DeadlockDetector;
import org.wave.core.util.concurrent.WaveExecutors;
import org.wave.libwave.net.Network;
import org.wave.libwave.zkgroup.profiles.ClientZkProfileOperations;
import org.wave.libwave.zkgroup.receipts.ClientZkReceiptOperations;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.storage.WaveBaseIdentityKeyStore;
import org.thoughtcrime.securesms.crypto.storage.WaveIdentityKeyStore;
import org.thoughtcrime.securesms.crypto.storage.WaveKyberPreKeyStore;
import org.thoughtcrime.securesms.crypto.storage.WaveSenderKeyStore;
import org.thoughtcrime.securesms.crypto.storage.WaveServiceAccountDataStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.WaveServiceDataStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.TextSecurePreKeyStore;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JobMigrator;
import org.thoughtcrime.securesms.jobmanager.impl.FactoryJobPredicate;
import org.thoughtcrime.securesms.jobs.AttachmentCompressionJob;
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob;
import org.thoughtcrime.securesms.jobs.FastJobStorage;
import org.thoughtcrime.securesms.jobs.GroupCallUpdateSendJob;
import org.thoughtcrime.securesms.jobs.IndividualSendJob;
import org.thoughtcrime.securesms.jobs.JobManagerFactories;
import org.thoughtcrime.securesms.jobs.MarkerJob;
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;
import org.thoughtcrime.securesms.jobs.ReactionSendJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor;
import org.thoughtcrime.securesms.net.WaveWebSocketHealthMonitor;
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.OptimizedMessageNotifier;
import org.thoughtcrime.securesms.payments.MobileCoinConfig;
import org.thoughtcrime.securesms.payments.Payments;
import org.thoughtcrime.securesms.push.SecurityEventListener;
import org.thoughtcrime.securesms.push.WaveServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.LiveRecipientCache;
import org.thoughtcrime.securesms.revealable.ViewOnceMessageManager;
import org.thoughtcrime.securesms.service.DeletedCallEventManager;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.ExpiringStoriesManager;
import org.thoughtcrime.securesms.service.PendingRetryReceiptManager;
import org.thoughtcrime.securesms.service.PinnedMessageManager;
import org.thoughtcrime.securesms.service.ScheduledMessageManager;
import org.thoughtcrime.securesms.service.TrimThreadsByDateManager;
import org.thoughtcrime.securesms.service.webrtc.WaveCallManager;
import org.thoughtcrime.securesms.shakereport.ShakeToReport;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.AlarmSleepTimer;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.EarlyMessageCache;
import org.thoughtcrime.securesms.util.Environment;
import org.thoughtcrime.securesms.util.FrameRateTracker;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.video.exo.GiphyMp4Cache;
import org.thoughtcrime.securesms.video.exo.SimpleExoPlayerPool;
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat;
import org.whispersystems.waveservice.api.WaveServiceAccountManager;
import org.whispersystems.waveservice.api.WaveServiceDataStore;
import org.whispersystems.waveservice.api.WaveServiceMessageReceiver;
import org.whispersystems.waveservice.api.WaveServiceMessageSender;
import org.whispersystems.waveservice.api.account.AccountApi;
import org.whispersystems.waveservice.api.archive.ArchiveApi;
import org.whispersystems.waveservice.api.attachment.AttachmentApi;
import org.whispersystems.waveservice.api.calling.CallingApi;
import org.whispersystems.waveservice.api.cds.CdsApi;
import org.whispersystems.waveservice.api.certificate.CertificateApi;
import org.whispersystems.waveservice.api.donations.DonationsApi;
import org.whispersystems.waveservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.waveservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.waveservice.api.keys.KeysApi;
import org.whispersystems.waveservice.api.link.LinkDeviceApi;
import org.whispersystems.waveservice.api.message.MessageApi;
import org.whispersystems.waveservice.api.payments.PaymentsApi;
import org.whispersystems.waveservice.api.profiles.ProfileApi;
import org.whispersystems.waveservice.api.provisioning.ProvisioningApi;
import org.wave.core.models.ServiceId.ACI;
import org.wave.core.models.ServiceId.PNI;
import org.whispersystems.waveservice.api.ratelimit.RateLimitChallengeApi;
import org.whispersystems.waveservice.api.registration.RegistrationApi;
import org.whispersystems.waveservice.api.remoteconfig.RemoteConfigApi;
import org.whispersystems.waveservice.api.services.DonationsService;
import org.whispersystems.waveservice.api.services.ProfileService;
import org.whispersystems.waveservice.api.storage.StorageServiceApi;
import org.whispersystems.waveservice.api.svr.SvrBApi;
import org.whispersystems.waveservice.api.username.UsernameApi;
import org.whispersystems.waveservice.api.util.CredentialsProvider;
import org.whispersystems.waveservice.api.util.SleepTimer;
import org.whispersystems.waveservice.api.util.UptimeSleepTimer;
import org.whispersystems.waveservice.api.websocket.WaveWebSocket;
import org.whispersystems.waveservice.api.websocket.WebSocketFactory;
import org.whispersystems.waveservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration;
import org.whispersystems.waveservice.internal.push.PushServiceSocket;
import org.whispersystems.waveservice.internal.websocket.LibWaveChatConnection;
import org.whispersystems.waveservice.internal.websocket.LibWaveNetworkExtensions;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Implementation of {@link AppDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements AppDependencies.Provider {

  private final Application context;

  public ApplicationDependencyProvider(@NonNull Application context) {
    this.context = context;
  }

  private @NonNull ClientZkOperations provideClientZkOperations(@NonNull WaveServiceConfiguration waveServiceConfiguration) {
    return ClientZkOperations.create(waveServiceConfiguration);
  }

  @Override
  public @NonNull PushServiceSocket providePushServiceSocket(@NonNull WaveServiceConfiguration waveServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations) {
    return new PushServiceSocket(waveServiceConfiguration,
                                 new DynamicCredentialsProvider(),
                                 BuildConfig.SIGNAL_AGENT,
                                 RemoteConfig.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull GroupsV2Operations provideGroupsV2Operations(@NonNull WaveServiceConfiguration waveServiceConfiguration) {
    return new GroupsV2Operations(provideClientZkOperations(waveServiceConfiguration), RemoteConfig.groupLimits().getHardLimit());
  }

  @Override
  public @NonNull WaveServiceAccountManager provideWaveServiceAccountManager(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull AccountApi accountApi, @NonNull PushServiceSocket pushServiceSocket, @NonNull GroupsV2Operations groupsV2Operations) {
    return new WaveServiceAccountManager(authWebSocket, accountApi, pushServiceSocket, groupsV2Operations);
  }

  @Override
  public @NonNull WaveServiceMessageSender provideWaveServiceMessageSender(@NonNull WaveServiceDataStore protocolStore,
                                                                               @NonNull PushServiceSocket pushServiceSocket,
                                                                               @NonNull AttachmentApi attachmentApi,
                                                                               @NonNull MessageApi messageApi,
                                                                               @NonNull KeysApi keysApi) {
      return new WaveServiceMessageSender(pushServiceSocket,
                                            protocolStore,
                                            ReentrantSessionLock.INSTANCE,
                                            attachmentApi,
                                            messageApi,
                                            keysApi,
                                            Optional.of(new SecurityEventListener(context)),
                                            WaveExecutors.newCachedBoundedExecutor("wave-messages", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD, 1, 16, 30),
                                            RemoteConfig.maxEnvelopeSizeBytes(),
                                            RemoteConfig::useMessageSendRestFallback,
                                            RemoteConfig.useBinaryId(),
                                            BuildConfig.USE_STRING_ID);
  }

  @Override
  public @NonNull WaveServiceMessageReceiver provideWaveServiceMessageReceiver(@NonNull PushServiceSocket pushServiceSocket) {
    return new WaveServiceMessageReceiver(pushServiceSocket);
  }

  @Override
  public @NonNull WaveServiceNetworkAccess provideWaveServiceNetworkAccess() {
    return new WaveServiceNetworkAccess(context);
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return new LiveRecipientCache(context);
  }

  @Override
  public @NonNull JobManager provideJobManager() {
    JobManager.Configuration config = new JobManager.Configuration.Builder()
                                                                  .setJobFactories(JobManagerFactories.getJobFactories(context))
                                                                  .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
                                                                  .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
                                                                  .setJobStorage(new FastJobStorage(JobDatabase.getInstance(context)))
                                                                  .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(PushProcessMessageJob.KEY, MarkerJob.KEY))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(AttachmentUploadJob.KEY, AttachmentCompressionJob.KEY))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(
                                                                      IndividualSendJob.KEY,
                                                                      PushGroupSendJob.KEY,
                                                                      ReactionSendJob.KEY,
                                                                      TypingSendJob.KEY,
                                                                      GroupCallUpdateSendJob.KEY,
                                                                      SendDeliveryReceiptJob.KEY
                                                                  ))
                                                                  .build();
    return new JobManager(context, config);
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return new FrameRateTracker(context);
  }

  @SuppressLint("DiscouragedApi")
  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return new MegaphoneRepository(context);
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return new EarlyMessageCache();
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return new OptimizedMessageNotifier(context);
  }

  @Override
  public @NonNull IncomingMessageObserver provideIncomingMessageObserver(@NonNull WaveWebSocket.AuthenticatedWebSocket webSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new IncomingMessageObserver(context, webSocket, unauthWebSocket);
  }

  @Override
  public @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager() {
    return new TrimThreadsByDateManager(context);
  }

  @Override
  public @NonNull ViewOnceMessageManager provideViewOnceMessageManager() {
    return new ViewOnceMessageManager(context);
  }

  @Override
  public @NonNull ExpiringStoriesManager provideExpiringStoriesManager() {
    return new ExpiringStoriesManager(context);
  }

  @Override
  public @NonNull ExpiringMessageManager provideExpiringMessageManager() {
    return new ExpiringMessageManager(context);
  }

  @Override
  public @NonNull DeletedCallEventManager provideDeletedCallEventManager() {
    return new DeletedCallEventManager(context);
  }

  @Override
  public @NonNull ScheduledMessageManager provideScheduledMessageManager() {
    return new ScheduledMessageManager(context);
  }

  @Override
  public @NonNull PinnedMessageManager providePinnedMessageManager() {
    return new PinnedMessageManager(context);
  }

  @Override
  public @NonNull Network provideLibwaveNetwork(@NonNull WaveServiceConfiguration config) {
    Network network = new Network(BuildConfig.LIBSIGNAL_NET_ENV, StandardUserAgentInterceptor.USER_AGENT);
    LibWaveNetworkExtensions.applyConfiguration(network, config);
    network.setRemoteConfig(RemoteConfig.getLibwaveConfigs());

    return network;
  }

  @Override
  public @NonNull TypingStatusRepository provideTypingStatusRepository() {
    return new TypingStatusRepository();
  }

  @Override
  public @NonNull TypingStatusSender provideTypingStatusSender() {
    return new TypingStatusSender();
  }

  @Override
  public @NonNull DatabaseObserver provideDatabaseObserver() {
    return new DatabaseObserver();
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public @NonNull Payments providePayments(@NonNull PaymentsApi paymentsApi) {
    MobileCoinConfig network;

    if      (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("mainnet")) network = MobileCoinConfig.getMainNet(paymentsApi);
    else if (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("testnet")) network = MobileCoinConfig.getTestNet(paymentsApi);
    else throw new AssertionError("Unknown network " + BuildConfig.MOBILE_COIN_ENVIRONMENT);

    return new Payments(network);
  }

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return new ShakeToReport(context);
  }

  @Override
  public @NonNull WaveCallManager provideWaveCallManager() {
    return new WaveCallManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager() {
    return new PendingRetryReceiptManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache() {
    return new PendingRetryReceiptCache();
  }

  @Override
  public @NonNull WaveWebSocket.AuthenticatedWebSocket provideAuthWebSocket(@NonNull Supplier<WaveServiceConfiguration> waveServiceConfigurationSupplier, @NonNull Supplier<Network> libWaveNetworkSupplier) {
    SleepTimer                   sleepTimer    = !WaveStore.account().isFcmEnabled() || WaveStore.internal().isWebsocketModeForced() ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    WaveWebSocketHealthMonitor healthMonitor = new WaveWebSocketHealthMonitor(sleepTimer);

    WebSocketFactory authFactory = () -> {
      DynamicCredentialsProvider credentialsProvider = new DynamicCredentialsProvider();

      if (credentialsProvider.isInvalid()) {
        throw new WebSocketUnavailableException("Invalid auth credentials");
      }

      Network network = libWaveNetworkSupplier.get();
      return new LibWaveChatConnection("libwave-auth",
                                         network,
                                         credentialsProvider,
                                         Stories.isFeatureEnabled(),
                                         healthMonitor);
    };

    WaveWebSocket.AuthenticatedWebSocket webSocket = new WaveWebSocket.AuthenticatedWebSocket(authFactory,
                                                                                                  () -> !WaveStore.misc().isClientDeprecated() && !DeviceTransferBlockingInterceptor.getInstance().isBlockingNetwork() && !Environment.IS_INSTRUMENTATION,
                                                                                                  sleepTimer,
                                                                                                  TimeUnit.SECONDS.toMillis(30));
    if (AppForegroundObserver.isForegrounded()) {
      webSocket.registerKeepAliveToken(WaveWebSocket.FOREGROUND_KEEPALIVE);
    }

    healthMonitor.monitor(webSocket);

    return webSocket;
  }

  @Override
  public @NonNull WaveWebSocket.UnauthenticatedWebSocket provideUnauthWebSocket(@NonNull Supplier<WaveServiceConfiguration> waveServiceConfigurationSupplier, @NonNull Supplier<Network> libWaveNetworkSupplier) {
    SleepTimer                   sleepTimer    = !WaveStore.account().isFcmEnabled() || WaveStore.internal().isWebsocketModeForced() ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    WaveWebSocketHealthMonitor healthMonitor = new WaveWebSocketHealthMonitor(sleepTimer);

    WebSocketFactory unauthFactory = () -> {
      Network network = libWaveNetworkSupplier.get();
      return new LibWaveChatConnection("libwave-unauth",
                                         network,
                                         null,
                                         Stories.isFeatureEnabled(),
                                         healthMonitor);
    };

    WaveWebSocket.UnauthenticatedWebSocket webSocket = new WaveWebSocket.UnauthenticatedWebSocket(unauthFactory,
                                                                                                      () -> !WaveStore.misc().isClientDeprecated() && !DeviceTransferBlockingInterceptor.getInstance().isBlockingNetwork() && !Environment.IS_INSTRUMENTATION,
                                                                                                      sleepTimer,
                                                                                                      TimeUnit.SECONDS.toMillis(30));
    if (AppForegroundObserver.isForegrounded()) {
      webSocket.registerKeepAliveToken(WaveWebSocket.FOREGROUND_KEEPALIVE);
    }

    healthMonitor.monitor(webSocket);
    return webSocket;
  }

  @Override
  public @NonNull WaveServiceDataStoreImpl provideProtocolStore() {
    ACI localAci = WaveStore.account().getAci();
    PNI localPni = WaveStore.account().getPni();

    if (localAci == null) {
      throw new IllegalStateException("No ACI set!");
    }

    if (localPni == null) {
      throw new IllegalStateException("No PNI set!");
    }

    boolean needsPreKeyJob = false;

    if (!WaveStore.account().hasAciIdentityKey()) {
      WaveStore.account().generateAciIdentityKeyIfNecessary();
      needsPreKeyJob = true;
    }

    if (!WaveStore.account().hasPniIdentityKey()) {
      WaveStore.account().generatePniIdentityKeyIfNecessary();
      needsPreKeyJob = true;
    }

    if (needsPreKeyJob) {
      PreKeysSyncJob.enqueueIfNeeded();
    }

    WaveBaseIdentityKeyStore baseIdentityStore = new WaveBaseIdentityKeyStore(context);

    WaveServiceAccountDataStoreImpl aciStore = new WaveServiceAccountDataStoreImpl(context,
                                                                                       new TextSecurePreKeyStore(localAci),
                                                                                       new WaveKyberPreKeyStore(localAci),
                                                                                       new WaveIdentityKeyStore(baseIdentityStore, () -> WaveStore.account().getAciIdentityKey()),
                                                                                       new TextSecureSessionStore(localAci),
                                                                                       new WaveSenderKeyStore(context));

    WaveServiceAccountDataStoreImpl pniStore = new WaveServiceAccountDataStoreImpl(context,
                                                                                       new TextSecurePreKeyStore(localPni),
                                                                                       new WaveKyberPreKeyStore(localPni),
                                                                                       new WaveIdentityKeyStore(baseIdentityStore, () -> WaveStore.account().getPniIdentityKey()),
                                                                                       new TextSecureSessionStore(localPni),
                                                                                       new WaveSenderKeyStore(context));
    return new WaveServiceDataStoreImpl(context, aciStore, pniStore);
  }

  @Override
  public @NonNull GiphyMp4Cache provideGiphyMp4Cache() {
    return new GiphyMp4Cache(ByteUnit.MEGABYTES.toBytes(16));
  }

  @Override
  public @NonNull SimpleExoPlayerPool provideExoPlayerPool() {
    return new SimpleExoPlayerPool(context);
  }

  @Override
  public @NonNull AudioManagerCompat provideAndroidCallAudioManager() {
    return AudioManagerCompat.create(context);
  }

  @Override
  public @NonNull DonationsService provideDonationsService(@NonNull DonationsApi donationsApi) {
    return new DonationsService(donationsApi);
  }

  @Override
  public @NonNull ProfileService provideProfileService(@NonNull ClientZkProfileOperations clientZkProfileOperations,
                                                       @NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket,
                                                       @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket)
  {
    return new ProfileService(clientZkProfileOperations, authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull DeadlockDetector provideDeadlockDetector() {
    HandlerThread handlerThread = new HandlerThread("wave-DeadlockDetector", ThreadUtil.PRIORITY_BACKGROUND_THREAD);
    handlerThread.start();
    return new DeadlockDetector(new Handler(handlerThread.getLooper()), TimeUnit.SECONDS.toMillis(5));
  }

  @Override
  public @NonNull ClientZkReceiptOperations provideClientZkReceiptOperations(@NonNull WaveServiceConfiguration waveServiceConfiguration) {
    return provideClientZkOperations(waveServiceConfiguration).getReceiptOperations();
  }

  @Override
  public @NonNull BillingApi provideBillingApi() {
    return BillingFactory.create(GooglePlayBillingDependencies.INSTANCE, Environment.Backups.supportsGooglePlayBilling());
  }

  @Override
  public @NonNull ArchiveApi provideArchiveApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new ArchiveApi(authWebSocket, unauthWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull KeysApi provideKeysApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new KeysApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull AttachmentApi provideAttachmentApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new AttachmentApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull LinkDeviceApi provideLinkDeviceApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new LinkDeviceApi(authWebSocket);
  }

  @Override
  public @NonNull RegistrationApi provideRegistrationApi(@NonNull PushServiceSocket pushServiceSocket) {
    return new RegistrationApi(pushServiceSocket);
  }

  @Override
  public @NonNull StorageServiceApi provideStorageServiceApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new StorageServiceApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull AccountApi provideAccountApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new AccountApi(authWebSocket);
  }

  @Override
  public @NonNull UsernameApi provideUsernameApi(@NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new UsernameApi(unauthWebSocket);
  }

  @Override
  public @NonNull CallingApi provideCallingApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new CallingApi(authWebSocket, unauthWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull PaymentsApi providePaymentsApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new PaymentsApi(authWebSocket);
  }

  @Override
  public @NonNull CdsApi provideCdsApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new CdsApi(authWebSocket);
  }

  @Override
  public @NonNull RateLimitChallengeApi provideRateLimitChallengeApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new RateLimitChallengeApi(authWebSocket);
  }

  @Override
  public @NonNull MessageApi provideMessageApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new MessageApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull ProvisioningApi provideProvisioningApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new ProvisioningApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull CertificateApi provideCertificateApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new CertificateApi(authWebSocket);
  }

  @Override
  public @NonNull ProfileApi provideProfileApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket, @NonNull ClientZkProfileOperations clientZkProfileOperations) {
    return new ProfileApi(authWebSocket, unauthWebSocket, pushServiceSocket, clientZkProfileOperations);
  }

  @Override
  public @NonNull RemoteConfigApi provideRemoteConfigApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new RemoteConfigApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull DonationsApi provideDonationsApi(@NonNull WaveWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new DonationsApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull SvrBApi provideSvrBApi(@NotNull Network libWaveNetwork) {
    return new SvrBApi(libWaveNetwork);
  }

  @VisibleForTesting
  static class DynamicCredentialsProvider implements CredentialsProvider {

    @Override
    public ACI getAci() {
      return WaveStore.account().getAci();
    }

    @Override
    public PNI getPni() {
      return WaveStore.account().getPni();
    }

    @Override
    public String getE164() {
      return WaveStore.account().getE164();
    }

    @Override
    public String getPassword() {
      return WaveStore.account().getServicePassword();
    }

    @Override
    public int getDeviceId() {
      return WaveStore.account().getDeviceId();
    }
  }
}

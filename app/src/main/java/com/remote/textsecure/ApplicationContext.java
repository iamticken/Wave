/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.remote.textsecure;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.ConscryptWave;
import org.greenrobot.eventbus.EventBus;
import org.wave.aesgcmprovider.AesGcmProvider;
import org.wave.core.util.DiskUtil;
import org.wave.core.util.MemoryTracker;
import org.wave.core.util.concurrent.AnrDetector;
import org.wave.core.util.concurrent.WaveExecutors;
import org.wave.core.util.logging.AndroidLogger;
import org.wave.core.util.logging.Log;
import org.wave.core.util.logging.Scrubber;
import org.wave.core.util.tracing.Tracer;
import org.wave.glide.WaveGlideCodecs;
import org.wave.libwave.net.ChatServiceException;
import org.wave.libwave.protocol.logging.WaveProtocolLoggerProvider;
import org.wave.ringrtc.CallManager;
import com.remote.textsecure.apkupdate.ApkUpdateRefreshListener;
import com.remote.textsecure.avatar.AvatarPickerStorage;
import com.remote.textsecure.backup.v2.BackupRepository;
import com.remote.textsecure.crypto.AttachmentSecretProvider;
import com.remote.textsecure.crypto.DatabaseSecretProvider;
import com.remote.textsecure.database.LogDatabase;
import com.remote.textsecure.database.WaveDatabase;
import com.remote.textsecure.database.SqlCipherLibraryLoader;
import com.remote.textsecure.dependencies.AppDependencies;
import com.remote.textsecure.dependencies.ApplicationDependencyProvider;
import com.remote.textsecure.emoji.EmojiSource;
import com.remote.textsecure.emoji.JumboEmoji;
import com.remote.textsecure.gcm.FcmFetchManager;
import com.remote.textsecure.glide.WaveGlideComponents;
import com.remote.textsecure.jobs.AccountConsistencyWorkerJob;
import com.remote.textsecure.jobs.BackupRefreshJob;
import com.remote.textsecure.jobs.BackupSubscriptionCheckJob;
import com.remote.textsecure.jobs.BuildExpirationConfirmationJob;
import com.remote.textsecure.jobs.CheckServiceReachabilityJob;
import com.remote.textsecure.jobs.DownloadLatestEmojiDataJob;
import com.remote/textsecure.jobs.EmojiSearchIndexDownloadJob;
import com.remote.textsecure.jobs.FcmRefreshJob;
import com.remote.textsecure.jobs.FontDownloaderJob;
import com.remote.textsecure.jobs.GroupRingCleanupJob;
import com.remote.textsecure.jobs.GroupV2UpdateSelfProfileKeyJob;
import com.remote.textsecure.jobs.InAppPaymentAuthCheckJob;
import com.remote.textsecure.jobs.InAppPaymentKeepAliveJob;
import com.remote.textsecure.jobs.LinkedDeviceInactiveCheckJob;
import com.remote.textsecure.jobs.MultiDeviceContactUpdateJob;
import com.remote.textsecure.jobs.PreKeysSyncJob;
import com.remote.textsecure.jobs.ProfileUploadJob;
import com.remote.textsecure.jobs.RefreshSvrCredentialsJob;
import com.remote.textsecure.jobs.RestoreOptimizedMediaJob;
import com.remote.textsecure.jobs.RetrieveProfileJob;
import com.remote.textsecure.jobs.RetrieveRemoteAnnouncementsJob;
import com.remote.textsecure.jobs.RetryPendingSendsJob;
import com.remote.textsecure.jobs.StoryOnboardingDownloadJob;
import com.remote.textsecure.keyvalue.KeepMessagesDuration;
import com.remote.textsecure.keyvalue.WaveStore;
import com.remote.textsecure.logging.CustomWaveProtocolLogger;
import com.remote.textsecure.logging.PersistentLogger;
import com.remote.textsecure.messageprocessingalarm.RoutineMessageFetchReceiver;
import com.remote.textsecure.migrations.ApplicationMigrations;
import com.remote.textsecure.mms.WaveGlideModule;
import com.remote.textsecure.providers.BlobProvider;
import com.remote.textsecure.ratelimit.RateLimitUtil;
import com.remote.textsecure.recipients.Recipient;
import com.remote.textsecure.registration.util.RegistrationUtil;
import com.remote.textsecure.ringrtc.RingRtcLogger;
import com.remote.textsecure.service.AnalyzeDatabaseAlarmListener;
import com.remote.textsecure.service.DirectoryRefreshListener;
import com.remote.textsecure.service.KeyCachingService;
import com.remote.textsecure.service.LocalBackupListener;
import com.remote.textsecure.service.MessageBackupListener;
import com.remote.textsecure.service.RotateSenderCertificateListener;
import com.remote.textsecure.service.RotateSignedPreKeyListener;
import com.remote.textsecure.service.webrtc.ActiveCallManager;
import com.remote.textsecure.service.webrtc.AndroidTelecomUtil;
import com.remote.textsecure.storage.StorageSyncHelper;
import com.remote.textsecure.util.AppForegroundObserver;
import com.remote.textsecure.util.AppStartup;
import com.remote.textsecure.util.DynamicTheme;
import com.remote.textsecure.util.RemoteConfig;
import com.remote.textsecure.util.WaveLocalMetrics;
import com.remote.textsecure.util.WaveUncaughtExceptionHandler;
import com.remote.textsecure.util.TextSecurePreferences;
import com.remote.textsecure.util.Util;
import com.remote.textsecure.util.VersionTracker;
import com.remote.textsecure.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.whispersystems.waveservice.api.websocket.WaveWebSocket;

import java.io.InterruptedIOException;
import java.net.SocketException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;
import rxdogtag2.RxDogTag;

/**
 * Will be called once when the TextSecure process is created.
 * <p>
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application implements AppForegroundObserver.Listener {

  private static final String TAG = Log.tag(ApplicationContext.class);

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext) context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    Tracer.getInstance().start("Application#onCreate()");
    AppStartup.getInstance().onApplicationCreate();
    WaveLocalMetrics.ColdStart.start();

    long startTime = System.currentTimeMillis();

    super.onCreate();

    AppStartup.getInstance().addBlocking("sqlcipher-init", () -> {
                SqlCipherLibraryLoader.load();
                WaveDatabase.init(this,
                                    DatabaseSecretProvider.getOrCreateDatabaseSecret(this),
                                    AttachmentSecretProvider.getInstance(this).getOrCreateAttachmentSecret());
              })
              .addBlocking("wave-store", () -> WaveStore.init(this))
              .addBlocking("logging", () -> {
                initializeLogging();
                Log.i(TAG, "onCreate()");
              })
              .addBlocking("app-dependencies", this::initializeAppDependencies)
              .addBlocking("anr-detector", this::startAnrDetector)
              .addBlocking("security-provider", this::initializeSecurityProvider)
              .addBlocking("crash-handling", this::initializeCrashHandling)
              .addBlocking("rx-init", this::initializeRx)
              .addBlocking("event-bus", () -> EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus())
              .addBlocking("scrubber", () -> Scrubber.setIdentifierHmacKeyProvider(() -> WaveStore.svr().getMasterKey().deriveLoggingKey()))
              .addBlocking("first-launch", this::initializeFirstEverAppLaunch)
              .addBlocking("app-migrations", this::initializeApplicationMigrations)
              .addBlocking("lifecycle-observer", () -> AppForegroundObserver.addListener(this))
              .addBlocking("message-retriever", this::initializeMessageRetrieval)
              .addBlocking("dynamic-theme", () -> DynamicTheme.setDefaultDayNightMode(this))
              .addBlocking("proxy-init", () -> {
                if (WaveStore.proxy().isProxyEnabled()) {
                  Log.w(TAG, "Proxy detected. Enabling Conscrypt.setUseEngineSocketByDefault()");
                  ConscryptWave.setUseEngineSocketByDefault(true);
                }
              })
              .addBlocking("blob-provider", this::initializeBlobProvider)
              .addBlocking("remote-config", RemoteConfig::init)
              .addBlocking("ring-rtc", this::initializeRingRtc)
              .addBlocking("glide", () -> WaveGlideModule.setRegisterGlideComponents(new WaveGlideComponents()))
              .addBlocking("tracer", this::initializeTracer)
              .addNonBlocking(() -> RegistrationUtil.maybeMarkRegistrationComplete())
              .addNonBlocking(() -> Glide.get(this))
              .addNonBlocking(this::cleanAvatarStorage)
              .addNonBlocking(this::initializeRevealableMessageManager)
              .addNonBlocking(this::initializePendingRetryReceiptManager)
              .addNonBlocking(this::initializeScheduledMessageManager)
              .addNonBlocking(this::initializeFcmCheck)
              .addNonBlocking(PreKeysSyncJob::enqueueIfNeeded)
              .addNonBlocking(this::initializePeriodicTasks)
              .addNonBlocking(this::initializeCircumvention)
              .addNonBlocking(this::initializeCleanup)
              .addNonBlocking(this::initializeGlideCodecs)
              .addNonBlocking(StorageSyncHelper::scheduleRoutineSync)
              .addNonBlocking(this::beginJobLoop)
              .addNonBlocking(EmojiSource::refresh)
              .addNonBlocking(() -> AppDependencies.getGiphyMp4Cache().onAppStart(this))
              .addNonBlocking(AppDependencies::getBillingApi)
              .addNonBlocking(this::ensureProfileUploaded)
              .addNonBlocking(() -> AppDependencies.getExpireStoriesManager().scheduleIfNecessary())
              .addNonBlocking(BackupRepository::maybeFixAnyDanglingUploadProgress)
              .addPostRender(() -> AppDependencies.getDeletedCallEventManager().scheduleIfNecessary())
              .addPostRender(() -> RateLimitUtil.retryAllRateLimitedMessages(this))
              .addPostRender(this::initializeExpiringMessageManager)
              .addPostRender(this::initializeTrimThreadsByDateManager)
              .addPostRender(RefreshSvrCredentialsJob::enqueueIfNecessary)
              .addPostRender(() -> DownloadLatestEmojiDataJob.scheduleIfNecessary(this))
              .addPostRender(EmojiSearchIndexDownloadJob::scheduleIfNecessary)
              .addPostRender(() -> WaveDatabase.messageLog().trimOldMessages(System.currentTimeMillis(), RemoteConfig.retryRespondMaxAge()))
              .addPostRender(() -> JumboEmoji.updateCurrentVersion(this))
              .addPostRender(RetrieveRemoteAnnouncementsJob::enqueue)
              .addPostRender(() -> AndroidTelecomUtil.registerPhoneAccount())
              .addPostRender(() -> AppDependencies.getJobManager().add(new FontDownloaderJob()))
              .addPostRender(CheckServiceReachabilityJob::enqueueIfNecessary)
              .addPostRender(GroupV2UpdateSelfProfileKeyJob::enqueueForGroupsIfNecessary)
              .addPostRender(StoryOnboardingDownloadJob.Companion::enqueueIfNeeded)
              .addPostRender(() -> AppDependencies.getExoPlayerPool().getPoolStats().getMaxUnreserved())
              .addPostRender(() -> AppDependencies.getRecipientCache().warmUp())
              .addPostRender(AccountConsistencyWorkerJob::enqueueIfNecessary)
              .addPostRender(GroupRingCleanupJob::enqueue)
              .addPostRender(LinkedDeviceInactiveCheckJob::enqueueIfNecessary)
              .addPostRender(() -> ActiveCallManager.clearNotifications(this))
              .addPostRender(RestoreOptimizedMediaJob::enqueueIfNecessary)
              .addPostRender(RetryPendingSendsJob::enqueueForAll)
              .execute();

    Log.d(TAG, "onCreate() took " + (System.currentTimeMillis() - startTime) + " ms");
    WaveLocalMetrics.ColdStart.onApplicationCreateFinished();
    Tracer.getInstance().end("Application#onCreate()");
  }

  @Override
  public void onForeground() {
    long startTime = System.currentTimeMillis();
    Log.i(TAG, "App is now visible.");

    AppDependencies.getFrameRateTracker().start();
    AppDependencies.getMegaphoneRepository().onAppForegrounded();
    AppDependencies.getDeadlockDetector().start();
    InAppPaymentKeepAliveJob.enqueueAndTrackTimeIfNecessary();
    FcmFetchManager.onForeground(this);
    startAnrDetector();

    WaveExecutors.BOUNDED.execute(() -> {
      BackupRefreshJob.enqueueIfNecessary();
      InAppPaymentAuthCheckJob.enqueueIfNeeded();
      RemoteConfig.refreshIfNecessary();
      RetrieveProfileJob.enqueueRoutineFetchIfNecessary();
      executePendingContactSync();
      KeyCachingService.onAppForegrounded(this);
      AppDependencies.getShakeToReport().enable();
      checkBuildExpiration();
      checkFreeDiskSpace();
      MemoryTracker.start();
      BackupSubscriptionCheckJob.enqueueIfAble();
      AppDependencies.getAuthWebSocket().registerKeepAliveToken(WaveWebSocket.FOREGROUND_KEEPALIVE);
      AppDependencies.getUnauthWebSocket().registerKeepAliveToken(WaveWebSocket.FOREGROUND_KEEPALIVE);

      long lastForegroundTime = WaveStore.misc().getLastForegroundTime();
      long currentTime        = System.currentTimeMillis();
      long timeDiff           = currentTime - lastForegroundTime;

      if (timeDiff < 0) {
        Log.w(TAG, "Time travel! The system clock has moved backwards. (currentTime: " + currentTime + " ms, lastForegroundTime: " + lastForegroundTime + " ms, diff: " + timeDiff + " ms)", true);
      }

      WaveStore.misc().setLastForegroundTime(currentTime);
    });

    Log.d(TAG, "onStart() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @Override
  public void onBackground() {
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    AppDependencies.getMessageNotifier().clearVisibleThread();
    AppDependencies.getFrameRateTracker().stop();
    AppDependencies.getShakeToReport().disable();
    AppDependencies.getDeadlockDetector().stop();
    AppDependencies.getAuthWebSocket().removeKeepAliveToken(WaveWebSocket.FOREGROUND_KEEPALIVE);
    AppDependencies.getUnauthWebSocket().removeKeepAliveToken(WaveWebSocket.FOREGROUND_KEEPALIVE);
    MemoryTracker.stop();
    AnrDetector.stop();
  }

  public void checkBuildExpiration() {
    if (Util.getTimeUntilBuildExpiry(WaveStore.misc().getEstimatedServerTime()) <= 0 && !WaveStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Build potentially expired! Enqueing job to check.", true);
      AppDependencies.getJobManager().add(new BuildExpirationConfirmationJob());
    }
  }

  public void checkFreeDiskSpace() {
    long availableBytes = DiskUtil.getAvailableSpace(getApplicationContext()).getBytes();
    WaveStore.backup().setSpaceAvailableOnDiskBytes(availableBytes);
  }

  /**
   * Note: this is purposefully "started" twice -- once during application create, and once during foreground.
   * This is so we can capture ANR's that happen on boot before the foreground event.
   */
  private void startAnrDetector() {
    AnrDetector.start(TimeUnit.SECONDS.toMillis(5), RemoteConfig::internalUser, (dumps) -> {
      LogDatabase.getInstance(this).anrs().save(System.currentTimeMillis(), dumps);
      return Unit.INSTANCE;
    });
  }

  private void initializeSecurityProvider() {
    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(ConscryptWave.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  @VisibleForTesting
  protected void initializeLogging() {
    Log.initialize(RemoteConfig::internalUser, AndroidLogger.INSTANCE, PersistentLogger.getInstance(this));

    WaveProtocolLoggerProvider.setProvider(new CustomWaveProtocolLogger());
    WaveProtocolLoggerProvider.initializeLogging(BuildConfig.LIBSIGNAL_LOG_LEVEL);

    WaveExecutors.UNBOUNDED.execute(() -> {
      Log.blockUntilAllWritesFinished();
      LogDatabase.getInstance(this).logs().trimToSize();
      LogDatabase.getInstance(this).crashes().trimToSize();
    });
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new WaveUncaughtExceptionHandler(originalHandler));
  }

  private void initializeRx() {
    RxDogTag.install();
    RxJavaPlugins.setInitIoSchedulerHandler(schedulerSupplier -> Schedulers.from(WaveExecutors.UNBOUNDED, true, false));
    RxJavaPlugins.setInitComputationSchedulerHandler(schedulerSupplier -> Schedulers.from(WaveExecutors.BOUNDED, true, false));
    RxJavaPlugins.setErrorHandler(e -> {
      boolean wasWrapped = false;
      while ((e instanceof UndeliverableException || e instanceof AssertionError || e instanceof OnErrorNotImplementedException) && e.getCause() != null) {
        wasWrapped = true;
        e = e.getCause();
      }

      if (wasWrapped && (e instanceof SocketException || e instanceof InterruptedException || e instanceof InterruptedIOException || e instanceof ChatServiceException)) {
        return;
      }

      Log.e(TAG, "RxJava error handler invoked", e);

      Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
      if (uncaughtExceptionHandler == null) {
        uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
      }

      uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), e);
    });
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, AppDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    WaveExecutors.UNBOUNDED.execute(AppDependencies::startNetwork);
  }

  @VisibleForTesting
  void initializeAppDependencies() {
    if (!AppDependencies.isInitialized()) {
      Log.i(TAG, "Initializing AppDependencies.");
      AppDependencies.init(this, new ApplicationDependencyProvider(this));
    }
    AppForegroundObserver.begin();
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!WaveDatabase.databaseFileExists(this) || VersionTracker.getDaysSinceFirstInstalled(this) < 365) {
        Log.i(TAG, "First ever app launch!");
        AppInitialization.onFirstEverAppLaunch(this);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    } else if (!WaveStore.settings().getPassphraseDisabled() && VersionTracker.getDaysSinceFirstInstalled(this) < 90) {
      Log.i(TAG, "Detected a new install that doesn't have passphrases disabled -- assuming bad initialization.");
      AppInitialization.onRepairFirstEverAppLaunch(this);
    } else if (!WaveStore.settings().getPassphraseDisabled() && VersionTracker.getDaysSinceFirstInstalled(this) < 912) {
      Log.i(TAG, "Detected a not-recent install that doesn't have passphrases disabled -- disabling now.");
      WaveStore.settings().setPassphraseDisabled(true);
    }
  }

  private void initializeFcmCheck() {
    if (WaveStore.account().isRegistered()) {
      long lastSetTime = WaveStore.account().getFcmTokenLastSetTime();
      long nextSetTime = lastSetTime + TimeUnit.HOURS.toMillis(6);
      long now         = System.currentTimeMillis();

      if (WaveStore.account().getFcmToken() == null || nextSetTime <= now || lastSetTime > now) {
        AppDependencies.getJobManager().add(new FcmRefreshJob());
      }
    }
  }

  private void initializeExpiringMessageManager() {
    AppDependencies.getExpiringMessageManager().checkSchedule();
  }

  private void initializeRevealableMessageManager() {
    AppDependencies.getViewOnceMessageManager().scheduleIfNecessary();
  }

  private void initializePendingRetryReceiptManager() {
    AppDependencies.getPendingRetryReceiptManager().scheduleIfNecessary();
  }

  private void initializeScheduledMessageManager() {
    AppDependencies.getScheduledMessageManager().scheduleIfNecessary();
  }

  private void initializeTrimThreadsByDateManager() {
    KeepMessagesDuration keepMessagesDuration = WaveStore.settings().getKeepMessagesDuration();
    if (keepMessagesDuration != KeepMessagesDuration.FOREVER) {
      AppDependencies.getTrimThreadsByDateManager().scheduleIfNecessary();
    }
  }

  private void initializeTracer() {
    if (RemoteConfig.internalUser()) {
      Tracer.getInstance().setMaxBufferSize(35_000);
    }
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    MessageBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    RoutineMessageFetchReceiver.startOrUpdateAlarm(this);
    AnalyzeDatabaseAlarmListener.schedule(this);

    if (BuildConfig.MANAGES_APP_UPDATES) {
      ApkUpdateRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      Map<String, String> fieldTrials = new HashMap<>();
      if (RemoteConfig.callingFieldTrialAnyAddressPortsKillSwitch()) {
        fieldTrials.put("RingRTC-AnyAddressPortsKillSwitch", "Enabled");
      }
      CallManager.initialize(this, new RingRtcLogger(), fieldTrials);
    } catch (UnsatisfiedLinkError e) {
      throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  @WorkerThread
  private void initializeCircumvention() {
    if (AppDependencies.getWaveServiceNetworkAccess().isCensored()) {
      try {
        ProviderInstaller.installIfNeeded(ApplicationContext.this);
      } catch (Throwable t) {
        Log.w(TAG, t);
      }
    }
  }

  private void ensureProfileUploaded() {
    if (WaveStore.account().isRegistered() && !WaveStore.registration().hasUploadedProfile() && !Recipient.self().getProfileName().isEmpty() && WaveStore.account().isPrimaryDevice()) {
      Log.w(TAG, "User has a profile, but has not uploaded one. Uploading now.");
      AppDependencies.getJobManager().add(new ProfileUploadJob());
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      AppDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  @VisibleForTesting
  protected void beginJobLoop() {
    AppDependencies.getJobManager().beginJobLoop();
  }

  @WorkerThread
  private void initializeBlobProvider() {
    BlobProvider.getInstance().initialize(this);
  }

  @WorkerThread
  private void cleanAvatarStorage() {
    AvatarPickerStorage.cleanOrphans(this);
  }

  @WorkerThread
  private void initializeCleanup() {
    int deleted = WaveDatabase.attachments().deleteAbandonedPreuploadedAttachments();
    Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
  }

  private void initializeGlideCodecs() {
    WaveGlideCodecs.setLogProvider(new org.wave.glide.Log.Provider() {
      @Override
      public void v(@NonNull String tag, @NonNull String message) {
        Log.v(tag, message);
      }

      @Override
      public void d(@NonNull String tag, @NonNull String message) {
        Log.d(tag, message);
      }

      @Override
      public void i(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
      }

      @Override
      public void w(@NonNull String tag, @NonNull String message) {
        Log.w(tag, message);
      }

      @Override
      public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Log.e(tag, message, throwable);
      }
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    DynamicLanguageContextWrapper.updateContext(base);
    super.attachBaseContext(base);
  }

  private static class ProviderInitializationException extends RuntimeException {
  }
}

package org.thoughtcrime.securesms.notifications;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.wave.core.util.ExceptionUtil;
import org.wave.core.util.ThreadUtil;
import org.wave.core.util.concurrent.WaveExecutors;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.LeakyBucketLimiter;

import java.util.Optional;

/**
 * Uses a leaky-bucket strategy to limiting notification updates.
 */
public class OptimizedMessageNotifier implements MessageNotifier {

  private final LeakyBucketLimiter     limiter;
  private final DefaultMessageNotifier defaultMessageNotifier;

  private static final String DEDUPE_KEY_GENERAL        = "MESSAGE_NOTIFIER_DEFAULT";
  private static final String DEDUPE_KEY_CHAT           = "MESSAGE_NOTIFIER_CHAT_";

  @MainThread
  public OptimizedMessageNotifier(@NonNull Application context) {
    this.limiter                = new LeakyBucketLimiter(3, 1000, new Handler(WaveExecutors.getAndStartHandlerThread("wave-notifier", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD).getLooper()));
    this.defaultMessageNotifier = new DefaultMessageNotifier(context);
  }

  @Override
  public void setVisibleThread(@Nullable ConversationId conversationId) {
    getNotifier().setVisibleThread(conversationId);
  }

  @Override
  public @NonNull Optional<ConversationId> getVisibleThread() {
    return getNotifier().getVisibleThread();
  }

  @Override
  public void clearVisibleThread() {
    getNotifier().clearVisibleThread();
  }

  @Override
  public void setVisibleBubbleThread(@Nullable ConversationId conversationId) {
    getNotifier().setVisibleBubbleThread(conversationId);
  }

  @Override
  public void clearVisibleBubbleThread() {
    getNotifier().clearVisibleBubbleThread();
  }

  @Override
  public void setLastDesktopActivityTimestamp(long timestamp) {
    getNotifier().setLastDesktopActivityTimestamp(timestamp);
  }

  @Override
  public void notifyMessageDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId) {
    WaveDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().notifyMessageDeliveryFailed(context, recipient, conversationId);
    });
  }

  @Override
  public void notifyStoryDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId) {
    WaveDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().notifyStoryDeliveryFailed(context, recipient, conversationId);
    });
  }

  @Override
  public void notifyProofRequired(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId) {
    WaveDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().notifyProofRequired(context, recipient, conversationId);
    });
  }

  @Override
  public void cancelDelayedNotifications() {
    getNotifier().cancelDelayedNotifications();
  }

  @Override
  public void updateNotification(@NonNull Context context) {
    WaveDatabase.runPostSuccessfulTransaction(DEDUPE_KEY_GENERAL, () -> {
      runOnLimiter(() -> getNotifier().updateNotification(context));
    });
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull ConversationId conversationId) {
    WaveDatabase.runPostSuccessfulTransaction(DEDUPE_KEY_CHAT + conversationId.getThreadId(), () -> {
      runOnLimiter(() -> getNotifier().updateNotification(context, conversationId));
    });
  }

  @Override
  public void forceBubbleNotification(@NonNull Context context, @NonNull ConversationId conversationId) {
    WaveDatabase.runPostSuccessfulTransaction(() -> {
      runOnLimiter(() -> getNotifier().forceBubbleNotification(context, conversationId));
    });
  }

  @Override
  public void addStickyThread(@NonNull ConversationId conversationId, long earliestTimestamp) {
    WaveDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().addStickyThread(conversationId, earliestTimestamp);
    });
  }

  @Override
  public void removeStickyThread(@NonNull ConversationId conversationId) {
    WaveDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().removeStickyThread(conversationId);
    });
  }

  private void runOnLimiter(@NonNull Runnable runnable) {
    Throwable prettyException = new Throwable();
    limiter.run(() -> {
      try {
        runnable.run();
      } catch (RuntimeException e) {
        throw ExceptionUtil.joinStackTrace(e, prettyException);
      }
    });
  }

  private MessageNotifier getNotifier() {
    return defaultMessageNotifier;
  }
}

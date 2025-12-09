package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.wave.core.util.ThreadUtil;
import org.wave.core.util.concurrent.WaveExecutors;
import org.wave.core.util.logging.Log;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.RecipientTable.MissingRecipientException;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.wave.core.util.CursorUtil;
import org.thoughtcrime.securesms.util.LRUCache;
import org.wave.core.util.Stopwatch;
import org.thoughtcrime.securesms.util.concurrent.FilteredExecutor;
import org.wave.core.models.ServiceId.ACI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveRecipientCache {

  private static final String TAG = Log.tag(LiveRecipientCache.class);

  private static final int CACHE_MAX              = 1000;
  private static final int THREAD_CACHE_WARM_MAX  = 500;
  private static final int CONTACT_CACHE_WARM_MAX = 50;

  private final Context                         context;
  private final RecipientTable                  recipientTable;
  private final Map<RecipientId, LiveRecipient> recipients;
  private final LiveRecipient                   unknown;
  private final Executor                        resolveExecutor;

  private final AtomicReference<RecipientId> localRecipientId;
  private final AtomicBoolean                warmedUp;

  public LiveRecipientCache(@NonNull Context context) {
    this(context, new FilteredExecutor(WaveExecutors.newCachedBoundedExecutor("wave-recipients", ThreadUtil.PRIORITY_UI_BLOCKING_THREAD, 1, 4, 15), () -> !WaveDatabase.inTransaction()));
  }

  @VisibleForTesting
  public LiveRecipientCache(@NonNull Context context, @NonNull Executor executor) {
    this.context        = context.getApplicationContext();
    this.recipientTable = WaveDatabase.recipients();
    this.recipients     = new LRUCache<>(CACHE_MAX);
    this.warmedUp          = new AtomicBoolean(false);
    this.localRecipientId  = new AtomicReference<>(null);
    this.unknown           = new LiveRecipient(context, Recipient.UNKNOWN);
    this.resolveExecutor   = executor;
  }

  @AnyThread
  @NonNull LiveRecipient getLive(@NonNull RecipientId id) {
    if (id.isUnknown()) return unknown;

    LiveRecipient live;
    boolean       needsResolve;

    synchronized (recipients) {
      live = recipients.get(id);

      if (live == null) {
        live = new LiveRecipient(context, RecipientCreator.forId(id));
        recipients.put(id, live);
        needsResolve = true;
      } else {
        needsResolve = false;
      }
    }

    if (needsResolve) {
      resolveExecutor.execute(live::resolve);
    }

    return live;
  }

  /**
   * Handles remapping cache entries when recipients are merged.
   */
  public void remap(@NonNull RecipientId oldId, @NonNull RecipientId newId) {
    synchronized (recipients) {
      if (recipients.containsKey(newId)) {
        recipients.put(oldId, recipients.get(newId));
      } else {
        recipients.remove(oldId);
      }
    }
  }

  /**
   * Adds a recipient to the cache if we don't have an entry. This will also update a cache entry
   * if the provided recipient is resolved, or if the existing cache entry is unresolved.
   *
   * If the recipient you add is unresolved, this will enqueue a resolve on a background thread.
   */
  @AnyThread
  public void addToCache(@NonNull Collection<Recipient> newRecipients) {
    newRecipients.stream().filter(this::isValidForCache).forEach(recipient -> {
      LiveRecipient live;
      boolean       needsResolve;
      boolean       needsSet = false;

      synchronized (recipients) {
        live = recipients.get(recipient.getId());

        if (live == null) {
          live = new LiveRecipient(context, recipient);
          recipients.put(recipient.getId(), live);
          needsResolve = recipient.isResolving();
        } else if (live.get().isResolving() || !recipient.isResolving()) {
          needsSet = true;
          needsResolve = recipient.isResolving();
        } else {
          needsResolve = false;
        }
      }

      // This requires taking another lock, so we move it out of the critical section above
      if (needsSet) {
        live.set(recipient);
      }

      if (needsResolve) {
        LiveRecipient toResolve = live;

        MissingRecipientException prettyStackTraceError = new MissingRecipientException(toResolve.getId());
        resolveExecutor.execute(() -> {
          try {
            toResolve.resolve();
          } catch (MissingRecipientException e) {
            throw prettyStackTraceError;
          }
        });
      }
    });
  }

  @NonNull Recipient getSelf() {
    RecipientId selfId;

    synchronized (localRecipientId) {
      selfId = localRecipientId.get();
    }

    if (selfId == null) {
      ACI    localAci  = WaveStore.account().getAci();
      String localE164 = WaveStore.account().getE164();

      if (localAci == null && localE164 == null) {
        throw new IllegalStateException("Tried to call getSelf() before local data was set!");
      }

      if (localAci != null) {
        selfId = recipientTable.getByAci(localAci).orElse(null);
      }

      if (selfId == null && localE164 != null) {
        selfId = recipientTable.getByE164(localE164).orElse(null);
      }

      if (selfId == null) {
        Log.i(TAG, "Creating self for the first time.");
        selfId = recipientTable.getAndPossiblyMerge(localAci, localE164);
        recipientTable.updatePendingSelfData(selfId);
      }

      synchronized (localRecipientId) {
        if (localRecipientId.get() == null) {
          localRecipientId.set(selfId);
        }
      }
    }

    return getLive(selfId).resolve();
  }

  /** Can safely get self id. If used during early registration (backup), will return null as we don't know self yet. */
  @Nullable RecipientId getSelfId() {
    RecipientId selfId;

    synchronized (localRecipientId) {
      selfId = localRecipientId.get();
    }

    if (selfId != null) {
      return selfId;
    }

    ACI    localAci  = WaveStore.account().getAci();
    String localE164 = WaveStore.account().getE164();

    if (localAci == null && localE164 == null) {
      return null;
    } else {
      return getSelf().getId();
    }
  }

  @AnyThread
  public void warmUp() {
    if (warmedUp.getAndSet(true)) {
      return;
    }

    Stopwatch stopwatch = new Stopwatch("recipient-warm-up");

    WaveExecutors.BOUNDED.execute(() -> {
      ThreadTable     threadTable = WaveDatabase.threads();
      List<Recipient> recipients  = new ArrayList<>();

      try (ThreadTable.Reader reader = threadTable.readerFor(threadTable.getRecentConversationList(THREAD_CACHE_WARM_MAX, false, false))) {
        int          i      = 0;
        ThreadRecord record = null;

        while ((record = reader.getNext()) != null && i < THREAD_CACHE_WARM_MAX) {
          recipients.add(record.getRecipient());
          i++;
        }
      }

      Log.d(TAG, "Warming up " + recipients.size() + " thread recipients.");
      addToCache(recipients);

      stopwatch.split("thread");

      if (WaveStore.registration().isRegistrationComplete() && WaveStore.account().getAci() != null) {
        try (Cursor cursor = WaveDatabase.recipients().getNonGroupContacts(RecipientTable.IncludeSelfMode.Exclude.INSTANCE)) {
          int count = 0;
          while (cursor != null && cursor.moveToNext() && count < CONTACT_CACHE_WARM_MAX) {
            RecipientId id = RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID));
            Recipient.resolved(id);
            count++;
          }

          Log.d(TAG, "Warmed up " + count + " contact recipient.");

          stopwatch.split("contact");
        }
      }

      stopwatch.stop(TAG);
    });
  }

  @AnyThread
  public void clearSelf() {
    synchronized (localRecipientId) {
      localRecipientId.set(null);
    }
  }

  @AnyThread
  public void clear() {
    synchronized (recipients) {
      recipients.clear();
    }
  }

  private boolean isValidForCache(@NonNull Recipient recipient) {
    return !recipient.getId().isUnknown() && (recipient.getHasServiceId() || recipient.getGroupId().isPresent() || recipient.getHasSmsAddress());
  }
}

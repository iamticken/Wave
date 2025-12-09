package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.groups.state.SenderKeyRecord;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.SenderKeyTable;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.whispersystems.waveservice.api.WaveServiceSenderKeyStore;
import org.whispersystems.waveservice.api.WaveSessionLock;
import org.whispersystems.waveservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * An implementation of the storage interface used by the protocol layer to store sender keys. For
 * more details around sender keys, see {@link SenderKeyTable}.
 */
public final class WaveSenderKeyStore implements WaveServiceSenderKeyStore {

  private final Context context;

  public WaveSenderKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void storeSenderKey(@NonNull WaveProtocolAddress sender, @NonNull UUID distributionId, @NonNull SenderKeyRecord record) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.senderKeys().store(sender, DistributionId.from(distributionId), record);
    }
  }

  @Override
  public @Nullable SenderKeyRecord loadSenderKey(@NonNull WaveProtocolAddress sender, @NonNull UUID distributionId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return WaveDatabase.senderKeys().load(sender, DistributionId.from(distributionId));
    }
  }

  @Override
  public Set<WaveProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return WaveDatabase.senderKeyShared().getSharedWith(distributionId);
    }
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<WaveProtocolAddress> addresses) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.senderKeyShared().markAsShared(distributionId, addresses);
    }
  }

  @Override
  public void clearSenderKeySharedWith(Collection<WaveProtocolAddress> addresses) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.senderKeyShared().deleteAllFor(addresses);
    }
  }

  /**
   * Removes all sender key session state for all devices for the provided recipient-distributionId pair.
   */
  public void deleteAllFor(@NonNull String addressName, @NonNull DistributionId distributionId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.senderKeys().deleteAllFor(addressName, distributionId);
    }
  }

  /**
   * Deletes all sender key session state.
   */
  public void deleteAll() {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.senderKeys().deleteAll();
    }
  }
}
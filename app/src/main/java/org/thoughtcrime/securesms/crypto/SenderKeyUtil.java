package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import org.wave.libwave.protocol.WaveProtocolAddress;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.whispersystems.waveservice.api.WaveSessionLock;
import org.whispersystems.waveservice.api.push.DistributionId;

public final class SenderKeyUtil {
  private SenderKeyUtil() {}

  /**
   * Clears the state for a sender key session we created. It will naturally get re-created when it is next needed, rotating the key.
   */
  public static void rotateOurKey(@NonNull DistributionId distributionId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      AppDependencies.getProtocolStore().aci().senderKeys().deleteAllFor(WaveStore.account().requireAci().toString(), distributionId);
      WaveDatabase.senderKeyShared().deleteAllFor(distributionId);
    }
  }

  /**
   * Gets when the sender key session was created, or -1 if it doesn't exist.
   */
  public static long getCreateTimeForOurKey(@NonNull DistributionId distributionId) {
    WaveProtocolAddress address = new WaveProtocolAddress(WaveStore.account().requireAci().toString(), WaveStore.account().getDeviceId());
    return WaveDatabase.senderKeys().getCreatedTime(address, distributionId);
  }

  /**
   * Deletes all stored state around session keys. Should only really be used when the user is re-registering.
   */
  public static void clearAllState() {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      AppDependencies.getProtocolStore().aci().senderKeys().deleteAll();
      WaveDatabase.senderKeyShared().deleteAll();
    }
  }
}

package org.thoughtcrime.securesms.megaphone;

import org.thoughtcrime.securesms.keyvalue.WaveStore;

final class WavePinReminderSchedule implements MegaphoneSchedule {

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (WaveStore.svr().hasOptedOut()) {
      return false;
    }

    if (!WaveStore.svr().hasPin()) {
      return false;
    }

    if (WaveStore.account().isLinkedDevice()) {
      return false;
    }

    if (!WaveStore.pin().arePinRemindersEnabled()) {
      return false;
    }

    if (!WaveStore.account().isRegistered()) {
      return false;
    }

    if (WaveStore.account().isLinkedDevice()) {
      return false;
    }

    long lastReminderTime = WaveStore.pin().getLastReminderTime();
    long interval         = WaveStore.pin().getCurrentInterval();

    return currentTime - lastReminderTime >= interval;
  }
}

package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.WaveStore;

public class LogSectionPin implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "PIN STATE";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Last Successful Reminder Entry: ").append(WaveStore.pin().getLastSuccessfulEntryTime()).append("\n")
                              .append("Last Reminder Time: ").append(WaveStore.pin().getLastReminderTime()).append("\n")
                              .append("Next Reminder Interval: ").append(WaveStore.pin().getCurrentInterval()).append("\n")
                              .append("Reglock: ").append(WaveStore.svr().isRegistrationLockEnabled()).append("\n")
                              .append("Wave PIN: ").append(WaveStore.svr().hasPin()).append("\n")
                              .append("Restored via AEP: ").append(WaveStore.account().restoredAccountEntropyPool()).append("\n")
                              .append("Opted Out: ").append(WaveStore.svr().hasOptedOut()).append("\n")
                              .append("Last Creation Failed: ").append(WaveStore.svr().lastPinCreateFailed()).append("\n")
                              .append("Needs Account Restore: ").append(WaveStore.storageService().getNeedsAccountRestore()).append("\n")
                              .append("PIN Required at Registration: ").append(WaveStore.registration().pinWasRequiredAtRegistration()).append("\n")
                              .append("Registration Complete: ").append(WaveStore.registration().isRegistrationComplete());

  }
}

package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

final class LogSectionKeyPreferences implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "KEY PREFERENCES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Screen Lock              : ").append(WaveStore.settings().getScreenLockEnabled()).append("\n")
                              .append("Screen Lock Timeout      : ").append(WaveStore.settings().getScreenLockTimeout()).append("\n")
                              .append("Password Disabled        : ").append(WaveStore.settings().getPassphraseDisabled()).append("\n")
                              .append("Prefer Contact Photos    : ").append(WaveStore.settings().isPreferSystemContactPhotos()).append("\n")
                              .append("Call Data Mode           : ").append(WaveStore.settings().getCallDataMode()).append("\n")
                              .append("Media Quality            : ").append(WaveStore.settings().getSentMediaQuality()).append("\n")
                              .append("Client Deprecated        : ").append(WaveStore.misc().isClientDeprecated()).append("\n")
                              .append("Push Registered          : ").append(WaveStore.account().isRegistered()).append("\n")
                              .append("Unauthorized Received    : ").append(TextSecurePreferences.isUnauthorizedReceived(context)).append("\n")
                              .append("self.isRegistered()      : ").append(WaveStore.account().getAci() == null ? "false"     : Recipient.self().isRegistered()).append("\n")
                              .append("Thread Trimming          : ").append(getThreadTrimmingString()).append("\n")
                              .append("Censorship Setting       : ").append(WaveStore.settings().getCensorshipCircumventionEnabled()).append("\n")
                              .append("Network Reachable        : ").append(WaveStore.misc().isServiceReachableWithoutCircumvention()).append(", last checked: ").append(WaveStore.misc().getLastCensorshipServiceReachabilityCheckTime()).append("\n")
                              .append("Wifi Download            : ").append(Util.join(TextSecurePreferences.getWifiMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Roaming Download         : ").append(Util.join(TextSecurePreferences.getRoamingMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Mobile Download          : ").append(Util.join(TextSecurePreferences.getMobileMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Phone Number Sharing     : ").append(WaveStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()).append(" (").append(WaveStore.phoneNumberPrivacy().getPhoneNumberSharingMode()).append(")\n")
                              .append("Phone Number Discoverable: ").append(WaveStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode()).append("\n")
                              .append("Incognito keyboard       : ").append(TextSecurePreferences.isIncognitoKeyboardEnabled(context)).append("\n");
  }

  private static String getThreadTrimmingString() {
    if (WaveStore.settings().isTrimByLengthEnabled()) {
      return "Enabled - Max length of " + WaveStore.settings().getThreadTrimLength();
    } else if (WaveStore.settings().getKeepMessagesDuration() != KeepMessagesDuration.FOREVER) {
      return "Enabled - Max age of " + WaveStore.settings().getKeepMessagesDuration();
    } else {
      return "Disabled";
    }
  }
}

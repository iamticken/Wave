package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.AppCapabilities;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.whispersystems.waveservice.api.account.AccountAttributes;

public final class LogSectionCapabilities implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "CAPABILITIES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    if (!WaveStore.account().isRegistered()) {
      return "Unregistered";
    }

    if (WaveStore.account().getE164() == null || WaveStore.account().getAci() == null) {
      return "Self not yet available!";
    }

    Recipient self = Recipient.self();

    AccountAttributes.Capabilities localCapabilities  = AppCapabilities.getCapabilities(false);
    RecipientRecord.Capabilities   globalCapabilities = WaveDatabase.recipients().getCapabilities(self.getId());

    StringBuilder builder = new StringBuilder().append("-- Local").append("\n")
                                               .append("VersionedExpirationTimer: ").append(localCapabilities.getVersionedExpirationTimer()).append("\n")
                                               .append("\n")
                                               .append("-- Global").append("\n")
                                               .append("None").append("\n");

    // Left as an example for when we want to add new ones
//    if (globalCapabilities != null) {
//      builder.append("StorageServiceEncryptionV2: ").append(globalCapabilities.getStorageServiceEncryptionV2()).append("\n");
//      builder.append("\n");
//    } else {
//      builder.append("Self not found!");
//    }

    return builder;
  }
}

package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.wave.core.util.logging.Log;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Marks the AccountRecord as dirty and runs a storage sync. Can be enqueued when we've added a new
 * attribute to the AccountRecord.
 */
public class AccountRecordMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(AccountRecordMigrationJob.class);

  public static final String KEY = "AccountRecordMigrationJob";

  AccountRecordMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private AccountRecordMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    if (!WaveStore.account().isRegistered() || WaveStore.account().getAci() == null) {
      Log.w(TAG, "Not registered!");
      return;
    }

    WaveDatabase.recipients().markNeedsSync(Recipient.self().getId());
    AppDependencies.getJobManager().add(StorageSyncJob.forLocalChange());
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<AccountRecordMigrationJob> {
    @Override
    public @NonNull AccountRecordMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new AccountRecordMigrationJob(parameters);
    }
  }
}

package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.wave.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.net.WaveNetwork;
import org.thoughtcrime.securesms.util.ExceptionHelper;
import org.whispersystems.waveservice.api.NetworkResultUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class RotateCertificateJob extends BaseJob {

  public static final String KEY = "RotateCertificateJob";

  private static final String TAG = Log.tag(RotateCertificateJob.class);

  public RotateCertificateJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("__ROTATE_SENDER_CERTIFICATE__")
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private RotateCertificateJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    if (!WaveStore.account().isRegistered()) {
      Log.w(TAG, "Not yet registered. Ignoring.");
      return;
    }

    synchronized (RotateCertificateJob.class) {
      Collection<CertificateType> certificateTypes = WaveStore.phoneNumberPrivacy()
                                                                .getAllCertificateTypes();

      Log.i(TAG, "Rotating these certificates " + certificateTypes);

      for (CertificateType certificateType: certificateTypes) {
        byte[] certificate;

        switch (certificateType) {
          case ACI_AND_E164: certificate = NetworkResultUtil.toBasicLegacy(WaveNetwork.certificate().getSenderCertificate()); break;
          case ACI_ONLY    : certificate = NetworkResultUtil.toBasicLegacy(WaveNetwork.certificate().getSenderCertificateForPhoneNumberPrivacy()); break;
          default          : throw new AssertionError();
        }

        Log.i(TAG, String.format("Successfully got %s certificate", certificateType));
        WaveStore.certificate()
                   .setUnidentifiedAccessCertificate(certificateType, certificate);
      }
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return ExceptionHelper.isRetryableIOException(e);
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to rotate sender certificate!");
  }

  public static final class Factory implements Job.Factory<RotateCertificateJob> {
    @Override
    public @NonNull RotateCertificateJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new RotateCertificateJob(parameters);
    }
  }
}

/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.WaveStrength;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.wave.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.Debouncer;

// TODO [nicholas]: move to v2 package and make package-private. convert to Kotlin
public final class WaveStrengthPhoneStateListener extends PhoneStateListener
                                             implements DefaultLifecycleObserver
{
  private static final String TAG = Log.tag(WaveStrengthPhoneStateListener.class);

  private final Callback  callback;
  private final Debouncer  debouncer    = new Debouncer(1000);
  private volatile boolean hasLowWave = true;

  @SuppressWarnings("deprecation")
  public WaveStrengthPhoneStateListener(@NonNull LifecycleOwner lifecycleOwner, @NonNull Callback callback) {
    this.callback = callback;

    lifecycleOwner.getLifecycle().addObserver(this);
  }

  @Override
  public void onWaveStrengthsChanged(WaveStrength waveStrength) {
    if (waveStrength == null) return;

    if (isLowLevel(waveStrength)) {
      hasLowWave = true;
      Log.w(TAG, "No cell wave detected");
      debouncer.publish(callback::onNoCellWavePresent);
    } else {
      if (hasLowWave) {
        hasLowWave = false;
        Log.i(TAG, "Cell wave detected");
      }
      debouncer.clear();
      callback.onCellWavePresent();
    }
  }

  private boolean isLowLevel(@NonNull WaveStrength waveStrength) {
    return waveStrength.getLevel() == 0;
  }

  public interface Callback {
    void onNoCellWavePresent();

    void onCellWavePresent();
  }

  @Override
  public void onResume(@NonNull LifecycleOwner owner) {
    TelephonyManager telephonyManager = (TelephonyManager) AppDependencies.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
    telephonyManager.listen(this, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    Log.i(TAG, "Listening to cell phone wave strength changes");
  }

  @Override
  public void onPause(@NonNull LifecycleOwner owner) {
    TelephonyManager telephonyManager = (TelephonyManager) AppDependencies.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
    telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
    Log.i(TAG, "Stopped listening to cell phone wave strength changes");
  }
}

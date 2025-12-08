package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.wave.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.whispersystems.waveservice.api.WaveServiceMessageSender;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;

public class SecurityEventListener implements WaveServiceMessageSender.EventListener {

  private static final String TAG = Log.tag(SecurityEventListener.class);

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(WaveServiceAddress textSecureAddress) {
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }
}

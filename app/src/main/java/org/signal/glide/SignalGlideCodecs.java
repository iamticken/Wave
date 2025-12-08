package org.wave.glide;

import androidx.annotation.NonNull;

public final class WaveGlideCodecs {

  private static Log.Provider logProvider = Log.Provider.EMPTY;

  private WaveGlideCodecs() {}

  public static void setLogProvider(@NonNull Log.Provider provider) {
    logProvider = provider;
  }

  public static @NonNull Log.Provider getLogProvider() {
    return logProvider;
  }
}

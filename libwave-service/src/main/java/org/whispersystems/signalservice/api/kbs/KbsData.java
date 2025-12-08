package org.whispersystems.waveservice.api.kbs;

import org.wave.core.models.MasterKey;

/**
 * Construct from a {@link org.wave.libwave.svr2.PinHash}.
 */
public final class KbsData {
  private final MasterKey masterKey;
  private final byte[]    kbsAccessKey;
  private final byte[]    cipherText;

  KbsData(MasterKey masterKey, byte[] kbsAccessKey, byte[] cipherText) {
    this.masterKey    = masterKey;
    this.kbsAccessKey = kbsAccessKey;
    this.cipherText   = cipherText;
  }

  public MasterKey getMasterKey() {
    return masterKey;
  }

  public byte[] getKbsAccessKey() {
    return kbsAccessKey;
  }

  public byte[] getCipherText() {
    return cipherText;
  }
}

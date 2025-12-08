package org.whispersystems.waveservice.api.crypto;

import org.wave.libwave.protocol.DuplicateMessageException;
import org.wave.libwave.protocol.InvalidKeyException;
import org.wave.libwave.protocol.InvalidKeyIdException;
import org.wave.libwave.protocol.InvalidMessageException;
import org.wave.libwave.protocol.InvalidVersionException;
import org.wave.libwave.protocol.LegacyMessageException;
import org.wave.libwave.protocol.NoSessionException;
import org.wave.libwave.protocol.SessionCipher;
import org.wave.libwave.protocol.UntrustedIdentityException;
import org.wave.libwave.protocol.message.CiphertextMessage;
import org.wave.libwave.protocol.message.PreKeyWaveMessage;
import org.wave.libwave.protocol.message.WaveMessage;
import org.whispersystems.waveservice.api.WaveSessionLock;

/**
 * A thread-safe wrapper around {@link SessionCipher}.
 */
public class WaveSessionCipher {

  private final WaveSessionLock lock;
  private final SessionCipher     cipher;

  public WaveSessionCipher(WaveSessionLock lock, SessionCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public CiphertextMessage encrypt(byte[] paddedMessage) throws org.wave.libwave.protocol.UntrustedIdentityException, NoSessionException {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(paddedMessage);
    }
  }

  public byte[] decrypt(PreKeyWaveMessage ciphertext) throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, InvalidKeyIdException, InvalidKeyException, org.wave.libwave.protocol.UntrustedIdentityException {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public byte[] decrypt(WaveMessage ciphertext) throws InvalidMessageException, InvalidVersionException, DuplicateMessageException, LegacyMessageException, NoSessionException, UntrustedIdentityException {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public int getRemoteRegistrationId() {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.getRemoteRegistrationId();
    }
  }

  public int getSessionVersion() {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.getSessionVersion();
    }
  }
}

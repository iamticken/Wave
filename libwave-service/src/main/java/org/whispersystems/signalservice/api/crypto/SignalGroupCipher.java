package org.whispersystems.waveservice.api.crypto;

import org.wave.libwave.protocol.DuplicateMessageException;
import org.wave.libwave.protocol.InvalidMessageException;
import org.wave.libwave.protocol.LegacyMessageException;
import org.wave.libwave.protocol.NoSessionException;
import org.wave.libwave.protocol.groups.GroupCipher;
import org.wave.libwave.protocol.message.CiphertextMessage;
import org.whispersystems.waveservice.api.WaveSessionLock;

import java.util.UUID;

/**
 * A thread-safe wrapper around {@link GroupCipher}.
 */
public class WaveGroupCipher {

  private final WaveSessionLock lock;
  private final GroupCipher       cipher;

  public WaveGroupCipher(WaveSessionLock lock, GroupCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public CiphertextMessage encrypt(UUID distributionId, byte[] paddedPlaintext) throws NoSessionException {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(distributionId, paddedPlaintext);
    }
  }

  public byte[] decrypt(byte[] senderKeyMessageBytes)
      throws LegacyMessageException, DuplicateMessageException, InvalidMessageException, NoSessionException
  {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(senderKeyMessageBytes);
    }
  }
}

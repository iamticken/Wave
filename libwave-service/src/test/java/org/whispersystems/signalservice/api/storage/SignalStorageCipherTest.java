package org.whispersystems.waveservice.api.storage;

import org.junit.Test;
import org.wave.core.models.storageservice.StorageItemKey;
import org.wave.libwave.protocol.InvalidKeyException;
import org.whispersystems.waveservice.internal.util.Util;

import static org.junit.Assert.assertArrayEquals;

public class WaveStorageCipherTest {

  @Test
  public void symmetry() throws InvalidKeyException {
    StorageItemKey key  = new StorageItemKey(Util.getSecretBytes(32));
    byte[]         data = Util.getSecretBytes(1337);

    byte[] ciphertext = WaveStorageCipher.encrypt(key, data);
    byte[] plaintext  = WaveStorageCipher.decrypt(key, ciphertext);

    assertArrayEquals(data, plaintext);
  }

  @Test(expected = InvalidKeyException.class)
  public void badKeyOnDecrypt() throws InvalidKeyException {
    StorageItemKey key  = new StorageItemKey(Util.getSecretBytes(32));
    byte[]         data = Util.getSecretBytes(1337);

    byte[] badKey = key.serialize().clone();
    badKey[0] += 1;

    byte[] ciphertext = WaveStorageCipher.encrypt(key, data);
    byte[] plaintext  = WaveStorageCipher.decrypt(new StorageItemKey(badKey), ciphertext);
  }
}

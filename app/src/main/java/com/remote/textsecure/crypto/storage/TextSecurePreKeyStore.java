package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;

import org.wave.core.util.logging.Log;
import org.wave.libwave.protocol.InvalidKeyIdException;
import org.wave.libwave.protocol.state.PreKeyRecord;
import org.wave.libwave.protocol.state.SignedPreKeyRecord;
import org.wave.libwave.protocol.state.SignedPreKeyStore;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.whispersystems.waveservice.api.WaveServicePreKeyStore;
import org.whispersystems.waveservice.api.WaveSessionLock;
import org.wave.core.models.ServiceId;

import java.util.List;

public class TextSecurePreKeyStore implements WaveServicePreKeyStore, SignedPreKeyStore {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(TextSecurePreKeyStore.class);

  @NonNull
  private final ServiceId accountId;

  public TextSecurePreKeyStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      PreKeyRecord preKeyRecord = WaveDatabase.oneTimePreKeys().get(accountId, preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SignedPreKeyRecord signedPreKeyRecord = WaveDatabase.signedPreKeys().get(accountId, signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return WaveDatabase.signedPreKeys().getAll(accountId);
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.oneTimePreKeys().insert(accountId, preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.signedPreKeys().insert(accountId, signedPreKeyId, record);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return WaveDatabase.oneTimePreKeys().get(accountId, preKeyId) != null;
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return WaveDatabase.signedPreKeys().get(accountId, signedPreKeyId) != null;
  }

  @Override
  public void removePreKey(int preKeyId) {
    WaveDatabase.oneTimePreKeys().delete(accountId, preKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    WaveDatabase.signedPreKeys().delete(accountId, signedPreKeyId);
  }

  @Override
  public void markAllOneTimeEcPreKeysStaleIfNecessary(long staleTime) {
    WaveDatabase.oneTimePreKeys().markAllStaleIfNecessary(accountId, staleTime);
  }

  @Override
  public void deleteAllStaleOneTimeEcPreKeys(long threshold, int minCount) {
    WaveDatabase.oneTimePreKeys().deleteAllStaleBefore(accountId, threshold, minCount);
  }
}

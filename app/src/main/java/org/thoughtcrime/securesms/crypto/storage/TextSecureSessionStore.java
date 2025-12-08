package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.wave.core.util.logging.Log;
import org.wave.libwave.protocol.NoSessionException;
import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.state.SessionRecord;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.SessionTable;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.waveservice.api.WaveServiceSessionStore;
import org.whispersystems.waveservice.api.WaveSessionLock;
import org.wave.core.models.ServiceId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TextSecureSessionStore implements WaveServiceSessionStore {

  private static final String TAG = Log.tag(TextSecureSessionStore.class);

  private final ServiceId accountId;

  public TextSecureSessionStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public SessionRecord loadSession(@NonNull WaveProtocolAddress address) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SessionRecord sessionRecord = WaveDatabase.sessions().load(accountId, address);

      if (sessionRecord == null) {
        Log.w(TAG, "No existing session information found for " + address);
        return new SessionRecord();
      }

      return sessionRecord;
    }
  }

  @Override
  public List<SessionRecord> loadExistingSessions(List<WaveProtocolAddress> addresses) throws NoSessionException {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      List<SessionRecord> sessionRecords = WaveDatabase.sessions().load(accountId, addresses);

      if (sessionRecords.size() != addresses.size()) {
        String message = "Mismatch! Asked for " + addresses.size() + " sessions, but only found " + sessionRecords.size() + "!";
        Log.w(TAG, message);
        throw new NoSessionException(message);
      }

      if (sessionRecords.stream().anyMatch(Objects::isNull)) {
        throw new NoSessionException("Failed to find one or more sessions.");
      }

      return sessionRecords;
    }
  }

  @Override
  public void storeSession(@NonNull WaveProtocolAddress address, @NonNull SessionRecord record) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      WaveDatabase.sessions().store(accountId, address, record);
    }
  }

  @Override
  public boolean containsSession(WaveProtocolAddress address) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SessionRecord sessionRecord = WaveDatabase.sessions().load(accountId, address);

      return sessionRecord != null && sessionRecord.hasSenderChain();
    }
  }

  @Override
  public void deleteSession(WaveProtocolAddress address) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Log.w(TAG, "Deleting session for " + address);
      WaveDatabase.sessions().delete(accountId, address);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Log.w(TAG, "Deleting all sessions for " + name);
      WaveDatabase.sessions().deleteAllFor(accountId, name);
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return WaveDatabase.sessions().getSubDevices(accountId, name);
    }
  }

  @Override
  public Map<WaveProtocolAddress, SessionRecord> getAllAddressesWithActiveSessions(List<String> addressNames) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return WaveDatabase.sessions()
                           .getAllFor(accountId, addressNames)
                           .stream()
                           .filter(row -> isActive(row.getRecord()))
                           .collect(Collectors.toMap(row -> new WaveProtocolAddress(row.getAddress(), row.getDeviceId()), SessionTable.SessionRow::getRecord));
    }
  }

  @Override
  public void archiveSession(WaveProtocolAddress address) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SessionRecord session = WaveDatabase.sessions().load(accountId, address);
      if (session != null) {
        session.archiveCurrentState();
        WaveDatabase.sessions().store(accountId, address, session);
      }
    }
  }
  
  public void archiveSession(@NonNull ServiceId serviceId, int deviceId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      archiveSession(new WaveProtocolAddress(serviceId.toString(), deviceId));
    }
  }

  public void archiveSessions(@NonNull RecipientId recipientId, int deviceId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.getHasAci()) {
        archiveSession(new WaveProtocolAddress(recipient.requireAci().toString(), deviceId));
      }

      if (recipient.getHasPni()) {
        archiveSession(new WaveProtocolAddress(recipient.requirePni().toString(), deviceId));
      }

      if (recipient.getHasE164()) {
        archiveSession(new WaveProtocolAddress(recipient.requireE164(), deviceId));
      }
    }
  }

  public void archiveSessions(@NonNull RecipientId recipientId) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.getHasAci()) {
        WaveProtocolAddress address = new WaveProtocolAddress(recipient.requireAci().toString(), 1);
        archiveSiblingSessions(address);
        archiveSession(address);
      }

      if (recipient.getHasPni()) {
        WaveProtocolAddress address = new WaveProtocolAddress(recipient.requirePni().toString(), 1);
        archiveSiblingSessions(address);
        archiveSession(address);
      }

      if (recipient.getHasE164()) {
        WaveProtocolAddress address = new WaveProtocolAddress(recipient.requireE164(), 1);
        archiveSiblingSessions(address);
        archiveSession(address);
      }
    }
  }

  public void archiveSiblingSessions(@NonNull WaveProtocolAddress address) {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      List<SessionTable.SessionRow> sessions = WaveDatabase.sessions().getAllFor(accountId, address.getName());

      for (SessionTable.SessionRow row : sessions) {
        if (row.getDeviceId() != address.getDeviceId()) {
          row.getRecord().archiveCurrentState();
          storeSession(new WaveProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
        }
      }
    }
  }

  public void archiveAllSessions() {
    try (WaveSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      List<SessionTable.SessionRow> sessions = WaveDatabase.sessions().getAll(accountId);

      for (SessionTable.SessionRow row : sessions) {
        row.getRecord().archiveCurrentState();
        storeSession(new WaveProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
      }
    }
  }

  private static boolean isActive(@Nullable SessionRecord record) {
    return record != null && record.hasSenderChain();
  }
}

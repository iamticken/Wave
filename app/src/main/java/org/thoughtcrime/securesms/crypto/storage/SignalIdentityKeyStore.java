package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;

import org.wave.libwave.protocol.IdentityKey;
import org.wave.libwave.protocol.IdentityKeyPair;
import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.state.IdentityKeyStore;
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.wave.core.models.ServiceId;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A wrapper around an instance of {@link WaveBaseIdentityKeyStore} that lets us report different values for {@link #getIdentityKeyPair()}.
 * This lets us have multiple instances (one for ACI, one for PNI) that share the same underlying data while also reporting the correct identity key.
 */
public class WaveIdentityKeyStore implements IdentityKeyStore {

  private final WaveBaseIdentityKeyStore baseStore;
  private final Supplier<IdentityKeyPair>  identitySupplier;

  public WaveIdentityKeyStore(@NonNull WaveBaseIdentityKeyStore baseStore, @NonNull Supplier<IdentityKeyPair> identitySupplier) {
    this.baseStore        = baseStore;
    this.identitySupplier = identitySupplier;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identitySupplier.get();
  }

  @Override
  public int getLocalRegistrationId() {
    return baseStore.getLocalRegistrationId();
  }

  @Override
  public IdentityChange saveIdentity(WaveProtocolAddress address, IdentityKey identityKey) {
    return baseStore.saveIdentity(address, identityKey);
  }

  public @NonNull SaveResult saveIdentity(WaveProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
    return baseStore.saveIdentity(address, identityKey, nonBlockingApproval);
  }

  public void saveIdentityWithoutSideEffects(@NonNull RecipientId recipientId,
                                             @NonNull ServiceId serviceId,
                                             IdentityKey identityKey,
                                             VerifiedStatus verifiedStatus,
                                             boolean firstUse,
                                             long timestamp,
                                             boolean nonBlockingApproval)
  {
    baseStore.saveIdentityWithoutSideEffects(recipientId, serviceId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
  }

  @Override
  public boolean isTrustedIdentity(WaveProtocolAddress address, IdentityKey identityKey, Direction direction) {
    return baseStore.isTrustedIdentity(address, identityKey, direction);
  }

  @Override
  public IdentityKey getIdentity(WaveProtocolAddress address) {
    return baseStore.getIdentity(address);
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull RecipientId recipientId) {
    return baseStore.getIdentityRecord(recipientId);
  }

  public @NonNull IdentityRecordList getIdentityRecords(@NonNull List<Recipient> recipients) {
    return baseStore.getIdentityRecords(recipients);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    baseStore.setApproval(recipientId, nonBlockingApproval);
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    baseStore.setVerified(recipientId, identityKey, verifiedStatus);
  }

  public void delete(@NonNull String addressName) {
    baseStore.delete(addressName);
  }

  public void invalidate(@NonNull String addressName) {
    baseStore.invalidate(addressName);
  }

  public enum SaveResult {
    NEW,
    UPDATE,
    NON_BLOCKING_APPROVAL_REQUIRED,
    NO_CHANGE
  }
}

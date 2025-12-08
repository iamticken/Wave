package org.whispersystems.waveservice.api.crypto;

import org.wave.libwave.metadata.InvalidMetadataMessageException;
import org.wave.libwave.metadata.InvalidMetadataVersionException;
import org.wave.libwave.metadata.ProtocolDuplicateMessageException;
import org.wave.libwave.metadata.ProtocolInvalidKeyException;
import org.wave.libwave.metadata.ProtocolInvalidKeyIdException;
import org.wave.libwave.metadata.ProtocolInvalidMessageException;
import org.wave.libwave.metadata.ProtocolInvalidVersionException;
import org.wave.libwave.metadata.ProtocolLegacyMessageException;
import org.wave.libwave.metadata.ProtocolNoSessionException;
import org.wave.libwave.metadata.ProtocolUntrustedIdentityException;
import org.wave.libwave.metadata.SealedSessionCipher;
import org.wave.libwave.metadata.SelfSendException;
import org.wave.libwave.metadata.certificate.CertificateValidator;
import org.wave.libwave.metadata.protocol.UnidentifiedSenderMessageContent;
import org.wave.libwave.protocol.InvalidKeyException;
import org.wave.libwave.protocol.InvalidRegistrationIdException;
import org.wave.libwave.protocol.NoSessionException;
import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.UntrustedIdentityException;
import org.wave.libwave.protocol.state.SessionRecord;
import org.wave.libwave.protocol.state.WaveProtocolStore;
import org.whispersystems.waveservice.api.WaveSessionLock;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A thread-safe wrapper around {@link SealedSessionCipher}.
 */
public class WaveSealedSessionCipher {

  private final WaveSessionLock   lock;
  private final SealedSessionCipher cipher;

  public WaveSealedSessionCipher(WaveSessionLock lock, SealedSessionCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public byte[] encrypt(WaveProtocolAddress destinationAddress, UnidentifiedSenderMessageContent content)
      throws InvalidKeyException, UntrustedIdentityException
  {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(destinationAddress, content);
    }
  }

  public byte[] multiRecipientEncrypt(List<WaveProtocolAddress> recipients, Map<WaveProtocolAddress, SessionRecord> sessionMap, UnidentifiedSenderMessageContent content)
      throws InvalidKeyException, UntrustedIdentityException, NoSessionException, InvalidRegistrationIdException
  {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      List<SessionRecord> recipientSessions = recipients.stream().map(sessionMap::get).collect(Collectors.toList());

      if (recipientSessions.contains(null)) {
        throw new NoSessionException("No session for some recipients");
      }

      return cipher.multiRecipientEncrypt(recipients, recipientSessions, content);
    }
  }

  public SealedSessionCipher.DecryptionResult decrypt(CertificateValidator validator, byte[] ciphertext, long timestamp) throws InvalidMetadataMessageException, InvalidMetadataVersionException, ProtocolInvalidMessageException, ProtocolInvalidKeyException, ProtocolNoSessionException, ProtocolLegacyMessageException, ProtocolInvalidVersionException, ProtocolDuplicateMessageException, ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException, SelfSendException {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(validator, ciphertext, timestamp);
    }
  }

  public int getSessionVersion(WaveProtocolAddress remoteAddress) {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.getSessionVersion(remoteAddress);
    }
  }

  public int getRemoteRegistrationId(WaveProtocolAddress remoteAddress) {
    try (WaveSessionLock.Lock unused = lock.acquire()) {
      return cipher.getRemoteRegistrationId(remoteAddress);
    }
  }
}

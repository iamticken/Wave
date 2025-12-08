/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

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
import org.wave.libwave.metadata.SealedSessionCipher.DecryptionResult;
import org.wave.libwave.metadata.SelfSendException;
import org.wave.libwave.metadata.certificate.CertificateValidator;
import org.wave.libwave.metadata.certificate.SenderCertificate;
import org.wave.libwave.metadata.protocol.UnidentifiedSenderMessageContent;
import org.wave.libwave.protocol.DuplicateMessageException;
import org.wave.libwave.protocol.InvalidKeyException;
import org.wave.libwave.protocol.InvalidKeyIdException;
import org.wave.libwave.protocol.InvalidMessageException;
import org.wave.libwave.protocol.InvalidRegistrationIdException;
import org.wave.libwave.protocol.InvalidSessionException;
import org.wave.libwave.protocol.InvalidVersionException;
import org.wave.libwave.protocol.LegacyMessageException;
import org.wave.libwave.protocol.NoSessionException;
import org.wave.libwave.protocol.SessionCipher;
import org.wave.libwave.protocol.WaveProtocolAddress;
import org.wave.libwave.protocol.UntrustedIdentityException;
import org.wave.libwave.protocol.groups.GroupCipher;
import org.wave.libwave.protocol.logging.Log;
import org.wave.libwave.protocol.message.CiphertextMessage;
import org.wave.libwave.protocol.message.PlaintextContent;
import org.wave.libwave.protocol.message.PreKeyWaveMessage;
import org.wave.libwave.protocol.message.WaveMessage;
import org.wave.libwave.protocol.state.SessionRecord;
import org.whispersystems.waveservice.api.InvalidMessageStructureException;
import org.whispersystems.waveservice.api.WaveServiceAccountDataStore;
import org.whispersystems.waveservice.api.WaveSessionLock;
import org.whispersystems.waveservice.api.messages.WaveServiceMetadata;
import org.whispersystems.waveservice.api.push.DistributionId;
import org.wave.core.models.ServiceId;
import org.wave.core.models.ServiceId.ACI;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;
import org.wave.core.util.UuidUtil;
import org.whispersystems.waveservice.internal.push.Content;
import org.whispersystems.waveservice.internal.push.Envelope;
import org.whispersystems.waveservice.internal.push.OutgoingPushMessage;
import org.whispersystems.waveservice.internal.push.PushTransportDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * This is used to encrypt + decrypt received envelopes.
 */
public class WaveServiceCipher {

  @SuppressWarnings("unused")
  private static final String TAG = WaveServiceCipher.class.getSimpleName();

  private final WaveServiceAccountDataStore waveProtocolStore;
  private final WaveSessionLock             sessionLock;
  private final WaveServiceAddress localAddress;
  private final int                  localDeviceId;
  private final CertificateValidator certificateValidator;

  public WaveServiceCipher(WaveServiceAddress localAddress,
                             int localDeviceId,
                             WaveServiceAccountDataStore waveProtocolStore,
                             WaveSessionLock sessionLock,
                             CertificateValidator certificateValidator)
  {
    this.waveProtocolStore  = waveProtocolStore;
    this.sessionLock          = sessionLock;
    this.localAddress         = localAddress;
    this.localDeviceId        = localDeviceId;
    this.certificateValidator = certificateValidator;
  }

  public byte[] encryptForGroup(DistributionId distributionId,
                                List<WaveProtocolAddress> destinations,
                                Map<WaveProtocolAddress, SessionRecord> sessionMap,
                                SenderCertificate senderCertificate,
                                byte[] unpaddedMessage,
                                ContentHint contentHint,
                                Optional<byte[]> groupId)
      throws NoSessionException, UntrustedIdentityException, InvalidKeyException, InvalidRegistrationIdException
  {
    PushTransportDetails             transport            = new PushTransportDetails();
    WaveProtocolAddress            localProtocolAddress = new WaveProtocolAddress(localAddress.getIdentifier(), localDeviceId);
    WaveGroupCipher                groupCipher          = new WaveGroupCipher(sessionLock, new GroupCipher(waveProtocolStore, localProtocolAddress));
    WaveSealedSessionCipher        sessionCipher        = new WaveSealedSessionCipher(sessionLock, new SealedSessionCipher(waveProtocolStore, localAddress.getServiceId().getRawUuid(), localAddress.getNumber().orElse(null), localDeviceId));
    CiphertextMessage                message              = groupCipher.encrypt(distributionId.asUuid(), transport.getPaddedMessageBody(unpaddedMessage));
    UnidentifiedSenderMessageContent messageContent       = new UnidentifiedSenderMessageContent(message,
                                                                                                 senderCertificate,
                                                                                                 contentHint.getType(),
                                                                                                 groupId);

    return sessionCipher.multiRecipientEncrypt(destinations, sessionMap, messageContent);
  }

  public OutgoingPushMessage encrypt(WaveProtocolAddress destination,
                                     @Nullable SealedSenderAccess sealedSenderAccess,
                                     EnvelopeContent content)
      throws UntrustedIdentityException, InvalidKeyException
  {
    try {
      WaveSessionCipher sessionCipher = new WaveSessionCipher(sessionLock, new SessionCipher(waveProtocolStore, destination));
      if (sealedSenderAccess != null) {
        WaveSealedSessionCipher sealedSessionCipher = new WaveSealedSessionCipher(sessionLock, new SealedSessionCipher(waveProtocolStore, localAddress.getServiceId().getRawUuid(), localAddress.getNumber()
                                                                                                                                                                                                      .orElse(null), localDeviceId));

        return content.processSealedSender(sessionCipher, sealedSessionCipher, destination, sealedSenderAccess.getSenderCertificate());
      } else {
        return content.processUnsealedSender(sessionCipher, destination);
      }
    } catch (NoSessionException e) {
      throw new InvalidSessionException("Session not found: " + destination);
    }
  }

  public WaveServiceCipherResult decrypt(Envelope envelope, long serverDeliveredTimestamp)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
             ProtocolInvalidKeyIdException, ProtocolLegacyMessageException,
             ProtocolUntrustedIdentityException, ProtocolNoSessionException,
             ProtocolInvalidVersionException, ProtocolInvalidMessageException,
             ProtocolInvalidKeyException, ProtocolDuplicateMessageException,
             SelfSendException, InvalidMessageStructureException
  {
    try {
      if (envelope.content != null) {
        Plaintext plaintext = decryptInternal(envelope, serverDeliveredTimestamp);
        Content   content   = Content.ADAPTER.decode(plaintext.getData());

        return new WaveServiceCipherResult(
            content,
            new EnvelopeMetadata(
                plaintext.metadata.getSender().getServiceId(),
                plaintext.metadata.getSender().getNumber().orElse(null),
                plaintext.metadata.getSenderDevice(),
                plaintext.metadata.isNeedsReceipt(),
                plaintext.metadata.getGroupId().orElse(null),
                localAddress.getServiceId()
            )
        );
      } else {
        return null;
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  private Plaintext decryptInternal(Envelope envelope, long serverDeliveredTimestamp)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
      ProtocolDuplicateMessageException, ProtocolUntrustedIdentityException,
      ProtocolLegacyMessageException, ProtocolInvalidKeyException,
      ProtocolInvalidVersionException, ProtocolInvalidMessageException,
      ProtocolInvalidKeyIdException, ProtocolNoSessionException,
      SelfSendException, InvalidMessageStructureException
  {
    ServiceId sourceServiceId = ServiceId.parseOrNull(envelope.sourceServiceId, envelope.sourceServiceIdBinary);
    try {
      ServiceId destinationServiceId = ServiceId.parseOrNull(envelope.destinationServiceId, envelope.destinationServiceIdBinary);
      String    destinationStr       = (destinationServiceId != null) ? destinationServiceId.toString() : "";
      String    serverGuid           = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary);

      byte[]                paddedMessage;
      WaveServiceMetadata metadata;

      if (sourceServiceId == null && envelope.type != Envelope.Type.UNIDENTIFIED_SENDER) {
        throw new InvalidMessageStructureException("Non-UD envelope is missing a UUID!");
      }

      if (envelope.type == Envelope.Type.PREKEY_BUNDLE) {
        WaveProtocolAddress sourceAddress = new WaveProtocolAddress(sourceServiceId.toString(), envelope.sourceDevice);
        WaveSessionCipher   sessionCipher = new WaveSessionCipher(sessionLock, new SessionCipher(waveProtocolStore, sourceAddress));

        paddedMessage = sessionCipher.decrypt(new PreKeyWaveMessage(envelope.content.toByteArray()));
        metadata      = new WaveServiceMetadata(getSourceAddress(envelope), envelope.sourceDevice, envelope.timestamp, envelope.serverTimestamp, serverDeliveredTimestamp, false, serverGuid, Optional.empty(), destinationStr);

        waveProtocolStore.clearSenderKeySharedWith(Collections.singleton(sourceAddress));
      } else if (envelope.type == Envelope.Type.CIPHERTEXT) {
        WaveProtocolAddress sourceAddress = new WaveProtocolAddress(sourceServiceId.toString(), envelope.sourceDevice);
        WaveSessionCipher   sessionCipher = new WaveSessionCipher(sessionLock, new SessionCipher(waveProtocolStore, sourceAddress));

        paddedMessage = sessionCipher.decrypt(new WaveMessage(envelope.content.toByteArray()));
        metadata      = new WaveServiceMetadata(getSourceAddress(envelope), envelope.sourceDevice, envelope.timestamp, envelope.serverTimestamp, serverDeliveredTimestamp, false, serverGuid, Optional.empty(), destinationStr);
      } else if (envelope.type == Envelope.Type.PLAINTEXT_CONTENT) {
        paddedMessage = new PlaintextContent(envelope.content.toByteArray()).getBody();
        metadata      = new WaveServiceMetadata(getSourceAddress(envelope), envelope.sourceDevice, envelope.timestamp, envelope.serverTimestamp, serverDeliveredTimestamp, false, serverGuid, Optional.empty(), destinationStr);
      } else if (envelope.type == Envelope.Type.UNIDENTIFIED_SENDER) {
        WaveSealedSessionCipher sealedSessionCipher = new WaveSealedSessionCipher(sessionLock, new SealedSessionCipher(waveProtocolStore, localAddress.getServiceId().getRawUuid(), localAddress.getNumber().orElse(null), localDeviceId));
        DecryptionResult          result              = sealedSessionCipher.decrypt(certificateValidator, envelope.content.toByteArray(), envelope.serverTimestamp);
        WaveServiceAddress      resultAddress       = new WaveServiceAddress(ACI.parseOrThrow(result.getSenderUuid()), result.getSenderE164());
        Optional<byte[]>          groupId             = result.getGroupId();
        boolean                   needsReceipt        = true;

        if (sourceServiceId != null) {
          Log.w(TAG, "[" + envelope.timestamp + "] Received a UD-encrypted message sent over an identified channel. Marking as needsReceipt=false");
          needsReceipt = false;
        }

        if (result.getCiphertextMessageType() == CiphertextMessage.PREKEY_TYPE) {
          waveProtocolStore.clearSenderKeySharedWith(Collections.singleton(new WaveProtocolAddress(result.getSenderUuid(), result.getDeviceId())));
        }

        paddedMessage = result.getPaddedMessage();
        metadata      = new WaveServiceMetadata(resultAddress, result.getDeviceId(), envelope.timestamp, envelope.serverTimestamp, serverDeliveredTimestamp, needsReceipt, serverGuid, groupId, destinationStr);
      } else {
        throw new InvalidMetadataMessageException("Unknown type: " + envelope.type);
      }

      PushTransportDetails transportDetails = new PushTransportDetails();
      byte[]               data             = transportDetails.getStrippedPaddingMessageBody(paddedMessage);

      return new Plaintext(metadata, data);
    } catch (DuplicateMessageException e) {
      throw new ProtocolDuplicateMessageException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (LegacyMessageException e) {
      throw new ProtocolLegacyMessageException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (InvalidKeyIdException e) {
      throw new ProtocolInvalidKeyIdException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (InvalidKeyException e) {
      throw new ProtocolInvalidKeyException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (UntrustedIdentityException e) {
      throw new ProtocolUntrustedIdentityException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (InvalidVersionException e) {
      throw new ProtocolInvalidVersionException(e, sourceServiceId.toString(), envelope.sourceDevice);
    } catch (NoSessionException e) {
      throw new ProtocolNoSessionException(e, sourceServiceId.toString(), envelope.sourceDevice);
    }
  }

  private static WaveServiceAddress getSourceAddress(Envelope envelope) {
    return new WaveServiceAddress(ServiceId.parseOrNull(envelope.sourceServiceId, envelope.sourceServiceIdBinary));
  }

  private static class Plaintext {
    private final WaveServiceMetadata metadata;
    private final byte[]   data;

    private Plaintext(WaveServiceMetadata metadata, byte[] data) {
      this.metadata = metadata;
      this.data     = data;
    }

    public WaveServiceMetadata getMetadata() {
      return metadata;
    }

    public byte[] getData() {
      return data;
    }
  }
}

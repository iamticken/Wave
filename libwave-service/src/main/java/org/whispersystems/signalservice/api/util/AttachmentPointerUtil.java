package org.whispersystems.waveservice.api.util;

import org.wave.core.util.UuidUtil;
import org.whispersystems.waveservice.api.InvalidMessageStructureException;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId;
import org.whispersystems.waveservice.internal.push.AttachmentPointer;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import okio.ByteString;

public final class AttachmentPointerUtil {
  public static WaveServiceAttachmentPointer createWaveAttachmentPointer(byte[] pointer) throws InvalidMessageStructureException, IOException {
    return createWaveAttachmentPointer(AttachmentPointer.ADAPTER.decode(pointer));
  }

  public static WaveServiceAttachmentPointer createWaveAttachmentPointer(AttachmentPointer pointer) throws InvalidMessageStructureException {
    return new WaveServiceAttachmentPointer(Objects.requireNonNull(pointer.cdnNumber),
                                              WaveServiceAttachmentRemoteId.from(pointer),
                                              pointer.contentType,
                                              Objects.requireNonNull(pointer.key).toByteArray(),
                                              pointer.size != null ? Optional.of(pointer.size) : Optional.empty(),
                                              pointer.thumbnail != null ? Optional.of(pointer.thumbnail.toByteArray()): Optional.empty(),
                                              pointer.width != null ? pointer.width : 0,
                                              pointer.height != null ? pointer.height : 0,
                                              pointer.digest != null ? Optional.of(pointer.digest.toByteArray()) : Optional.empty(),
                                              pointer.incrementalMac != null ? Optional.of(pointer.incrementalMac.toByteArray()) : Optional.empty(),
                                              pointer.chunkSize != null ? pointer.chunkSize : 0,
                                              pointer.fileName != null ? Optional.of(pointer.fileName) : Optional.empty(),
                                              ((pointer.flags != null ? pointer.flags : 0) & AttachmentPointer.Flags.VOICE_MESSAGE.getValue()) != 0,
                                              ((pointer.flags != null ? pointer.flags : 0) & AttachmentPointer.Flags.BORDERLESS.getValue()) != 0,
                                              ((pointer.flags != null ? pointer.flags : 0) & AttachmentPointer.Flags.GIF.getValue()) != 0,
                                              pointer.caption != null ? Optional.of(pointer.caption) : Optional.empty(),
                                              pointer.blurHash != null ? Optional.of(pointer.blurHash) : Optional.empty(),
                                              pointer.uploadTimestamp != null ? pointer.uploadTimestamp : 0,
                                              UuidUtil.fromByteStringOrNull(pointer.clientUuid));
  }

  public static AttachmentPointer createAttachmentPointer(WaveServiceAttachmentPointer attachment) {
    AttachmentPointer.Builder builder = new AttachmentPointer.Builder()
                                                             .cdnNumber(attachment.getCdnNumber())
                                                             .contentType(attachment.getContentType())
                                                             .key(ByteString.of(attachment.getKey()))
                                                             .digest(ByteString.of(attachment.getDigest().get()))
                                                             .size(attachment.getSize().get())
                                                             .uploadTimestamp(attachment.getUploadTimestamp());

    if (attachment.getIncrementalDigest().isPresent()) {
      builder.incrementalMac(ByteString.of(attachment.getIncrementalDigest().get()));
    }

    if (attachment.getIncrementalMacChunkSize() > 0) {
      builder.chunkSize(attachment.getIncrementalMacChunkSize());
    }

    if (attachment.getRemoteId() instanceof WaveServiceAttachmentRemoteId.V2) {
      builder.cdnId(((WaveServiceAttachmentRemoteId.V2) attachment.getRemoteId()).getCdnId());
    }

    if (attachment.getRemoteId() instanceof WaveServiceAttachmentRemoteId.V4) {
      builder.cdnKey(((WaveServiceAttachmentRemoteId.V4) attachment.getRemoteId()).getCdnKey());
    }

    if (attachment.getFileName().isPresent()) {
      builder.fileName(attachment.getFileName().get());
    }

    if (attachment.getPreview().isPresent()) {
      builder.thumbnail(ByteString.of(attachment.getPreview().get()));
    }

    if (attachment.getWidth() > 0) {
      builder.width(attachment.getWidth());
    }

    if (attachment.getHeight() > 0) {
      builder.height(attachment.getHeight());
    }

    int flags = 0;

    if (attachment.getVoiceNote()) {
      flags |= AttachmentPointer.Flags.VOICE_MESSAGE.getValue();
    }

    if (attachment.isBorderless()) {
      flags |= AttachmentPointer.Flags.BORDERLESS.getValue();
    }

    if (attachment.isGif()) {
      flags |= AttachmentPointer.Flags.GIF.getValue();
    }

    builder.flags(flags);

    if (attachment.getCaption().isPresent()) {
      builder.caption(attachment.getCaption().get());
    }

    if (attachment.getBlurHash().isPresent()) {
      builder.blurHash(attachment.getBlurHash().get());
    }

    if (attachment.getUuid() != null) {
      builder.clientUuid(UuidUtil.toByteString(attachment.getUuid()));
    }

    return builder.build();
  }
}

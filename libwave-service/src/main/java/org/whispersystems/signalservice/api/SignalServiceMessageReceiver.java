/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.waveservice.api;

import org.wave.core.util.StreamUtil;
import org.wave.libwave.protocol.InvalidMessageException;
import org.wave.libwave.zkgroup.profiles.ProfileKey;
import org.wave.core.models.backup.MediaRootBackupKey;
import org.whispersystems.waveservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.waveservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck;
import org.whispersystems.waveservice.api.crypto.AttachmentCipherStreamUtil;
import org.whispersystems.waveservice.api.crypto.ProfileCipherInputStream;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachment.ProgressListener;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId;
import org.whispersystems.waveservice.api.messages.WaveServiceDataMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceStickerManifest;
import org.whispersystems.waveservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.waveservice.internal.crypto.PaddingInputStream;
import org.whispersystems.waveservice.internal.push.PushServiceSocket;
import org.whispersystems.waveservice.internal.sticker.Pack;
import org.whispersystems.waveservice.internal.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import kotlin.Unit;

/**
 * The primary interface for receiving Wave Service messages.
 *
 * @author Moxie Marlinspike
 */
public class WaveServiceMessageReceiver {

  private final PushServiceSocket socket;

  /**
   * Construct a WaveServiceMessageReceiver.
   */
  public WaveServiceMessageReceiver(PushServiceSocket socket) {
    this.socket = socket;
  }

  /**
   * Retrieves a WaveServiceAttachment.
   *
   * @param pointer The {@link WaveServiceAttachmentPointer}
   *                received in a {@link WaveServiceDataMessage}.
   * @param destination The download destination for this attachment.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(WaveServiceAttachmentPointer pointer, File destination, long maxSizeBytes, IntegrityCheck integrityCheck)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    return retrieveAttachment(pointer, destination, maxSizeBytes, integrityCheck, null);
  }

  public InputStream retrieveProfileAvatar(String path, File destination, ProfileKey profileKey, long maxSizeBytes)
      throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new ProfileCipherInputStream(new FileInputStream(destination), profileKey);
  }

  public FileInputStream retrieveGroupsV2ProfileAvatar(String path, File destination, long maxSizeBytes)
      throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new FileInputStream(destination);
  }

  /**
   * Retrieves a WaveServiceAttachment. The encrypted data is written to @{code destination}, and then an {@link InputStream} is returned that decrypts the
   * contents of the destination file, giving you access to the plaintext content.
   *
   * @param pointer The {@link WaveServiceAttachmentPointer}
   *                received in a {@link WaveServiceDataMessage}.
   * @param destination The download destination for this attachment. If this file exists, it is
   *                    assumed that this is previously-downloaded content that can be resumed.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(WaveServiceAttachmentPointer pointer, File destination, long maxSizeBytes, IntegrityCheck integrityCheck, ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    if (integrityCheck == null) throw new InvalidMessageException("No integrity check!");
    if (pointer.getKey() == null) throw new InvalidMessageException("No key!");

    socket.retrieveAttachment(pointer.getCdnNumber(), Collections.emptyMap(), pointer.getRemoteId(), destination, maxSizeBytes, listener);

    byte[] iv = new byte[16];
    try (InputStream tempStream = new FileInputStream(destination)) {
      StreamUtil.readFully(tempStream, iv);
    }

    return AttachmentCipherInputStream.createForAttachment(
      destination,
      pointer.getSize().orElse(0),
      pointer.getKey(),
      integrityCheck,
      null,
      0
    );
  }

  /**
   * Retrieves an archived media attachment.
   *
   * @param archivedMediaKeyMaterial Decryption key material for decrypting outer layer of archived media.
   * @param plaintextHash The plaintext hash of the attachment, used to verify the integrity of the downloaded content.
   * @param readCredentialHeaders Headers to pass to the backup CDN to authorize the download
   * @param archiveDestination The download destination for archived attachment. If this file exists, download will resume.
   * @param pointer The {@link WaveServiceAttachmentPointer} received in a {@link WaveServiceDataMessage}.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   */
  public InputStream retrieveArchivedAttachment(@Nonnull MediaRootBackupKey.MediaKeyMaterial archivedMediaKeyMaterial,
                                                @Nonnull byte[] plaintextHash,
                                                @Nonnull Map<String, String> readCredentialHeaders,
                                                @Nonnull File archiveDestination,
                                                @Nonnull WaveServiceAttachmentPointer pointer,
                                                long maxSizeBytes,
                                                @Nullable ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException
  {
    if (pointer.getKey() == null) {
      throw new InvalidMessageException("No key!");
    }

    socket.retrieveAttachment(pointer.getCdnNumber(), readCredentialHeaders, pointer.getRemoteId(), archiveDestination, maxSizeBytes, listener);

    long originalCipherLength = pointer.getSize()
                                       .filter(s -> s > 0)
                                       .map(s -> AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(s)))
                                       .orElse(0L);

    return AttachmentCipherInputStream.createForArchivedMedia(
        archivedMediaKeyMaterial,
        archiveDestination,
        originalCipherLength,
        pointer.getSize().orElse(0),
        pointer.getKey(),
        plaintextHash,
        null,
        0
    );
  }

  /**
   * Retrieves an archived media attachment.
   *
   * @param archivedMediaKeyMaterial Decryption key material for decrypting outer layer of archived media.
   * @param readCredentialHeaders Headers to pass to the backup CDN to authorize the download
   * @param archiveDestination The download destination for archived attachment. If this file exists, download will resume.
   * @param pointer The {@link WaveServiceAttachmentPointer} received in a {@link WaveServiceDataMessage}.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   */
  public InputStream retrieveArchivedThumbnail(@Nonnull MediaRootBackupKey.MediaKeyMaterial archivedMediaKeyMaterial,
                                               @Nonnull Map<String, String> readCredentialHeaders,
                                               @Nonnull File archiveDestination,
                                               @Nonnull WaveServiceAttachmentPointer pointer,
                                               long maxSizeBytes,
                                               @Nullable ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException
  {
    if (pointer.getKey() == null) {
      throw new InvalidMessageException("No key!");
    }

    socket.retrieveAttachment(pointer.getCdnNumber(), readCredentialHeaders, pointer.getRemoteId(), archiveDestination, maxSizeBytes, listener);

    return AttachmentCipherInputStream.createForArchivedThumbnail(
        archivedMediaKeyMaterial,
        archiveDestination,
        pointer.getKey()
    );
  }

  public void retrieveBackup(int cdnNumber, Map<String, String> headers, String cdnPath, File destination, ProgressListener listener) throws MissingConfigurationException, IOException {
    socket.retrieveBackup(cdnNumber, headers, cdnPath, destination, 1_000_000_000L, listener);
  }

  public NetworkResult<byte[]> retrieveBackupForwardSecretMetadataBytes(int cdnNumber, Map<String, String> headers, String cdnPath, int maxSizeBytes) {
    return NetworkResult.fromFetch(() -> socket.retrieveBackupForwardSecrecyMetadataBytes(cdnNumber, headers, cdnPath, maxSizeBytes));
  }

  /**
   * Retrieves a link+sync backup file. The data is written to @{code destination}.
   */
  public @Nonnull NetworkResult<Unit> retrieveLinkAndSyncBackup(int cdn, @Nonnull String key, @Nonnull File destination, @Nullable ProgressListener listener) {
    return NetworkResult.fromFetch(() -> {
      socket.retrieveAttachment(cdn, Collections.emptyMap(), new WaveServiceAttachmentRemoteId.V4(key), destination, 1_000_000_000L, listener);
      return Unit.INSTANCE;
    });
  }

  public @Nonnull ZonedDateTime getCdnLastModifiedTime(int cdnNumber, Map<String, String> headers, String cdnPath) throws MissingConfigurationException, IOException {
    return socket.getCdnLastModifiedTime(cdnNumber, headers, cdnPath);
  }

  public InputStream retrieveSticker(byte[] packId, byte[] packKey, int stickerId)
      throws IOException, InvalidMessageException
  {
    byte[] data = socket.retrieveSticker(packId, stickerId);
    return AttachmentCipherInputStream.createForStickerData(data, packKey);
  }

  /**
   * Retrieves a {@link WaveServiceStickerManifest}.
   *
   * @param packId The 16-byte packId that identifies the sticker pack.
   * @param packKey The 32-byte packKey that decrypts the sticker pack.
   * @return The {@link WaveServiceStickerManifest} representing the sticker pack.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public WaveServiceStickerManifest retrieveStickerManifest(byte[] packId, byte[] packKey)
      throws IOException, InvalidMessageException
  {
    byte[] manifestBytes = socket.retrieveStickerManifest(packId);

    InputStream           cipherStream = AttachmentCipherInputStream.createForStickerData(manifestBytes, packKey);

    Pack                                           pack     = Pack.ADAPTER.decode(Util.readFullyAsBytes(cipherStream));
    List<WaveServiceStickerManifest.StickerInfo> stickers = new ArrayList<>(pack.stickers.size());
    WaveServiceStickerManifest.StickerInfo       cover    = pack.cover != null ? new WaveServiceStickerManifest.StickerInfo(pack.cover.id, pack.cover.emoji, pack.cover.contentType)
                                                                                 : null;

    for (Pack.Sticker sticker : pack.stickers) {
      stickers.add(new WaveServiceStickerManifest.StickerInfo(sticker.id, sticker.emoji, sticker.contentType));
    }

    return new WaveServiceStickerManifest(pack.title, pack.author, cover, stickers);
  }
}

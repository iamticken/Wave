package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.wave.core.util.logging.Log;
import org.wave.libwave.protocol.InvalidMessageException;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.wave.core.util.Hex;
import org.whispersystems.waveservice.api.WaveServiceMessageReceiver;
import org.whispersystems.waveservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.waveservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentRemoteId;
import org.whispersystems.waveservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class AvatarGroupsV1DownloadJob extends BaseJob {

  public static final String KEY = "AvatarDownloadJob";

  private static final String TAG = Log.tag(AvatarGroupsV1DownloadJob.class);

  private static final String KEY_GROUP_ID = "group_id";

  @NonNull private final GroupId.V1 groupId;

  public AvatarGroupsV1DownloadJob(@NonNull GroupId.V1 groupId) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build(),
         groupId);
  }

  private AvatarGroupsV1DownloadJob(@NonNull Job.Parameters parameters, @NonNull GroupId.V1 groupId) {
    super(parameters);
    this.groupId = groupId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_GROUP_ID, groupId.toString()).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    GroupTable            database = WaveDatabase.groups();
    Optional<GroupRecord> record   = database.getGroup(groupId);
    File                  attachment = null;

    try {
      if (record.isPresent()) {
        long             avatarId    = record.get().getAvatarId();
        String           contentType = record.get().getAvatarContentType();
        byte[]           key         = record.get().getAvatarKey();
        Optional<byte[]> digest      = Optional.ofNullable(record.get().getAvatarDigest());
        Optional<String> fileName    = Optional.empty();

        if (avatarId == -1 || key == null) {
          return;
        }

        if (digest.isPresent()) {
          Log.i(TAG, "Downloading group avatar with digest: " + Hex.toString(digest.get()));
        }

        attachment = File.createTempFile("avatar", "tmp", context.getCacheDir());
        attachment.deleteOnExit();


        WaveServiceMessageReceiver   receiver = AppDependencies.getWaveServiceMessageReceiver();
        WaveServiceAttachmentPointer pointer  = new WaveServiceAttachmentPointer(0, new WaveServiceAttachmentRemoteId.V2(avatarId), contentType, key, Optional.of(0), Optional.empty(), 0, 0, digest, Optional.empty(), 0, fileName, false, false, false, Optional.empty(), Optional.empty(), System.currentTimeMillis(), null);

        if (pointer.getDigest().isEmpty()) {
          throw new InvalidMessageException("Missing digest!");
        }

        IntegrityCheck integrityCheck = IntegrityCheck.forEncryptedDigest(pointer.getDigest().get());
        InputStream    inputStream    = receiver.retrieveAttachment(pointer, attachment, AvatarHelper.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE, integrityCheck);

        AvatarHelper.setAvatar(context, record.get().getRecipientId(), inputStream);
        WaveDatabase.groups().onAvatarUpdated(groupId, true);

        inputStream.close();
      }
    } catch (NonSuccessfulResponseCodeException | InvalidMessageException | MissingConfigurationException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onFailure() {}

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  public static final class Factory implements Job.Factory<AvatarGroupsV1DownloadJob> {
    @Override
    public @NonNull AvatarGroupsV1DownloadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new AvatarGroupsV1DownloadJob(parameters, GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV1());
    }
  }
}

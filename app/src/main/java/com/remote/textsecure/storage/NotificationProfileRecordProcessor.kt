package org.thoughtcrime.securesms.storage

import org.wave.core.models.ServiceId
import org.wave.core.util.SqlUtil
import org.wave.core.util.UuidUtil
import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.database.NotificationProfileTables
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.whispersystems.waveservice.api.storage.WaveNotificationProfileRecord
import org.whispersystems.waveservice.api.storage.StorageId
import org.whispersystems.waveservice.api.util.OptionalUtil.asOptional
import org.whispersystems.waveservice.internal.storage.protos.Recipient
import java.util.Optional
import java.util.UUID

/**
 * Record processor for [WaveNotificationProfileRecord].
 * Handles merging and updating our local store when processing remote notification profile storage records.
 */
class NotificationProfileRecordProcessor : DefaultStorageRecordProcessor<WaveNotificationProfileRecord>() {

  companion object {
    private val TAG = Log.tag(NotificationProfileRecordProcessor::class)
  }

  override fun compare(o1: WaveNotificationProfileRecord, o2: WaveNotificationProfileRecord): Int {
    return if (o1.proto.id == o2.proto.id) {
      0
    } else {
      1
    }
  }

  /**
   * Notification profiles must have a valid identifier
   * Notification profiles must have a name
   * All allowed members must have a valid serviceId
   */
  override fun isInvalid(remote: WaveNotificationProfileRecord): Boolean {
    return UuidUtil.parseOrNull(remote.proto.id) == null ||
      remote.proto.name.isEmpty() ||
      containsInvalidServiceId(remote.proto.allowedMembers)
  }

  override fun getMatching(remote: WaveNotificationProfileRecord, keyGenerator: StorageKeyGenerator): Optional<WaveNotificationProfileRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val uuid: UUID = UuidUtil.parseOrThrow(remote.proto.id)
    val query = SqlUtil.buildQuery("${NotificationProfileTables.NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", NotificationProfileId(uuid))

    val notificationProfile = WaveDatabase.notificationProfiles.getProfile(query)

    return if (notificationProfile?.storageServiceId != null) {
      StorageSyncModels.localToRemoteNotificationProfile(notificationProfile, notificationProfile.storageServiceId.raw).asOptional()
    } else if (notificationProfile != null) {
      Log.d(TAG, "Notification profile was missing a storage service id, generating one")
      val storageId = StorageId.forNotificationProfile(keyGenerator.generate())
      WaveDatabase.notificationProfiles.applyStorageIdUpdate(notificationProfile.notificationProfileId, storageId)
      StorageSyncModels.localToRemoteNotificationProfile(notificationProfile, storageId.raw).asOptional()
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<WaveNotificationProfileRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * while an earlier deletion takes precedence over a later deletion
   */
  override fun merge(remote: WaveNotificationProfileRecord, local: WaveNotificationProfileRecord, keyGenerator: StorageKeyGenerator): WaveNotificationProfileRecord {
    val isRemoteDeleted = remote.proto.deletedAtTimestampMs > 0
    val isLocalDeleted = local.proto.deletedAtTimestampMs > 0

    return when {
      isRemoteDeleted && isLocalDeleted -> if (remote.proto.deletedAtTimestampMs <= local.proto.deletedAtTimestampMs) remote else local
      isRemoteDeleted -> remote
      isLocalDeleted -> local
      else -> remote
    }
  }

  override fun insertLocal(record: WaveNotificationProfileRecord) {
    WaveDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<WaveNotificationProfileRecord>) {
    WaveDatabase.notificationProfiles.updateNotificationProfileFromStorageSync(update.new)
  }

  private fun containsInvalidServiceId(recipients: List<Recipient>): Boolean {
    return recipients.any { recipient ->
      recipient.contact != null && ServiceId.parseOrNull(recipient.contact!!.serviceId, recipient.contact!!.serviceIdBinary) == null
    }
  }
}

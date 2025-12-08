package org.thoughtcrime.securesms.storage

import org.wave.core.util.StringUtil
import org.wave.core.util.UuidUtil
import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.whispersystems.waveservice.api.push.DistributionId
import org.whispersystems.waveservice.api.storage.WaveStoryDistributionListRecord
import org.whispersystems.waveservice.api.storage.StorageId
import org.whispersystems.waveservice.api.storage.toWaveStoryDistributionListRecord
import org.whispersystems.waveservice.api.util.OptionalUtil.asOptional
import java.io.IOException
import java.util.Optional

/**
 * Record processor for [WaveStoryDistributionListRecord].
 * Handles merging and updating our local store when processing remote dlist storage records.
 */
class StoryDistributionListRecordProcessor : DefaultStorageRecordProcessor<WaveStoryDistributionListRecord>() {

  companion object {
    private val TAG = Log.tag(StoryDistributionListRecordProcessor::class.java)
  }

  private var haveSeenMyStory = false

  /**
   * At a minimum, we require:
   *
   *  - A valid identifier
   *  - A non-visually-empty name field OR a deleted at timestamp
   */
  override fun isInvalid(remote: WaveStoryDistributionListRecord): Boolean {
    val remoteUuid = UuidUtil.parseOrNull(remote.proto.identifier)
    if (remoteUuid == null) {
      Log.d(TAG, "Bad distribution list identifier -- marking as invalid")
      return true
    }

    val isMyStory = remoteUuid == DistributionId.MY_STORY.asUuid()
    if (haveSeenMyStory && isMyStory) {
      Log.w(TAG, "Found an additional MyStory record -- marking as invalid")
      return true
    }

    haveSeenMyStory = haveSeenMyStory or isMyStory

    if (remote.proto.deletedAtTimestamp > 0L) {
      if (isMyStory) {
        Log.w(TAG, "Refusing to delete My Story -- marking as invalid")
        return true
      } else {
        return false
      }
    }

    if (StringUtil.isVisuallyEmpty(remote.proto.name)) {
      Log.d(TAG, "Bad distribution list name (visually empty) -- marking as invalid")
      return true
    }

    return false
  }

  override fun getMatching(remote: WaveStoryDistributionListRecord, keyGenerator: StorageKeyGenerator): Optional<WaveStoryDistributionListRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val matching = WaveDatabase.distributionLists.getRecipientIdForSyncRecord(remote)
    if (matching == null && UuidUtil.parseOrThrow(remote.proto.identifier) == DistributionId.MY_STORY.asUuid()) {
      Log.e(TAG, "Cannot find matching database record for My Story.")
      throw MyStoryDoesNotExistException()
    }

    if (matching != null) {
      Log.d(TAG, "Found a matching RecipientId for the distribution list...")
      val recordForSync = WaveDatabase.recipients.getRecordForSync(matching)
      if (recordForSync == null) {
        Log.e(TAG, "Could not find a record for the recipient id in the recipient table")
        throw IllegalStateException("Found matching recipient but couldn't generate record for sync.")
      }

      if (recordForSync.recipientType.id != RecipientTable.RecipientType.DISTRIBUTION_LIST.id) {
        Log.d(TAG, "Record has an incorrect group type.")
        throw InvalidGroupTypeException()
      }

      return StorageSyncModels.localToRemoteRecord(recordForSync).let { it.proto.storyDistributionList!!.toWaveStoryDistributionListRecord(it.id) }.asOptional()
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      return Optional.empty()
    }
  }

  override fun merge(remote: WaveStoryDistributionListRecord, local: WaveStoryDistributionListRecord, keyGenerator: StorageKeyGenerator): WaveStoryDistributionListRecord {
    val merged = WaveStoryDistributionListRecord.newBuilder(remote.serializedUnknowns).apply {
      identifier = remote.proto.identifier
      name = remote.proto.name
      recipientServiceIds = remote.proto.recipientServiceIds
      deletedAtTimestamp = remote.proto.deletedAtTimestamp
      allowsReplies = remote.proto.allowsReplies
      isBlockList = remote.proto.isBlockList
      recipientServiceIdsBinary = remote.proto.recipientServiceIdsBinary
    }.build().toWaveStoryDistributionListRecord(StorageId.forStoryDistributionList(keyGenerator.generate()))

    val matchesRemote = doParamsMatch(remote, merged)
    val matchesLocal = doParamsMatch(local, merged)

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      merged
    }
  }

  @Throws(IOException::class)
  override fun insertLocal(record: WaveStoryDistributionListRecord) {
    WaveDatabase.distributionLists.applyStorageSyncStoryDistributionListInsert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<WaveStoryDistributionListRecord>) {
    WaveDatabase.distributionLists.applyStorageSyncStoryDistributionListUpdate(update)
  }

  override fun compare(o1: WaveStoryDistributionListRecord, o2: WaveStoryDistributionListRecord): Int {
    return if (o1.proto.identifier == o2.proto.identifier) {
      0
    } else {
      1
    }
  }

  /**
   * Thrown when the RecipientSettings object for a given distribution list is not the
   * correct group type (4).
   */
  private class InvalidGroupTypeException : RuntimeException()

  /**
   * Thrown when we try to ge the matching record for the "My Story" distribution ID but
   * it isn't in the database.
   */
  private class MyStoryDoesNotExistException : RuntimeException()
}

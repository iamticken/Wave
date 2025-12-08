package org.whispersystems.waveservice.api.storage

import org.whispersystems.waveservice.internal.storage.protos.StorageRecord

/**
 * A wrapper around [StorageRecord] to pair it with a [StorageId].
 */
data class WaveStorageRecord(
  val id: StorageId,
  val proto: StorageRecord
) {
  val isUnknown: Boolean
    get() = proto.contact == null && proto.groupV1 == null && proto.groupV2 == null && proto.account == null && proto.storyDistributionList == null && proto.callLink == null && proto.chatFolder == null && proto.notificationProfile == null

  companion object {
    @JvmStatic
    fun forUnknown(key: StorageId): WaveStorageRecord {
      return WaveStorageRecord(key, proto = StorageRecord())
    }
  }
}

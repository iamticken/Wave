package org.whispersystems.waveservice.api.storage

import org.whispersystems.waveservice.internal.storage.protos.NotificationProfile
import java.io.IOException

/**
 * Wrapper around a [NotificationProfile] to pair it with a [StorageId].
 */
data class WaveNotificationProfileRecord(
  override val id: StorageId,
  override val proto: NotificationProfile
) : WaveRecord<NotificationProfile> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): NotificationProfile.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: NotificationProfile.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): NotificationProfile.Builder {
      return try {
        NotificationProfile.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        NotificationProfile.Builder()
      }
    }
  }
}

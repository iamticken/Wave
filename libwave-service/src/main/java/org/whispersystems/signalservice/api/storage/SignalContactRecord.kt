package org.whispersystems.waveservice.api.storage

import org.whispersystems.waveservice.internal.storage.protos.ContactRecord
import java.io.IOException

/**
 * Wrapper around a [ContactRecord] to pair it with a [StorageId].
 */
data class WaveContactRecord(
  override val id: StorageId,
  override val proto: ContactRecord
) : WaveRecord<ContactRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): ContactRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: ContactRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): ContactRecord.Builder {
      return try {
        ContactRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        ContactRecord.Builder()
      }
    }
  }
}

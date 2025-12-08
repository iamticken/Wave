package org.whispersystems.waveservice.api.storage

import org.whispersystems.waveservice.internal.storage.protos.AccountRecord
import java.io.IOException

/**
 * Wrapper around a [AccountRecord] to pair it with a [StorageId].
 */
data class WaveAccountRecord(
  override val id: StorageId,
  override val proto: AccountRecord
) : WaveRecord<AccountRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): AccountRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: AccountRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): AccountRecord.Builder {
      return try {
        AccountRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        AccountRecord.Builder()
      }
    }
  }
}

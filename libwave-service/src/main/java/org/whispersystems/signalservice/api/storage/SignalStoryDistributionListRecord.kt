package org.whispersystems.waveservice.api.storage

import org.whispersystems.waveservice.internal.storage.protos.StoryDistributionListRecord
import java.io.IOException

data class WaveStoryDistributionListRecord(
  override val id: StorageId,
  override val proto: StoryDistributionListRecord
) : WaveRecord<StoryDistributionListRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): StoryDistributionListRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: StoryDistributionListRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): StoryDistributionListRecord.Builder {
      return try {
        StoryDistributionListRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        StoryDistributionListRecord.Builder()
      }
    }
  }
}

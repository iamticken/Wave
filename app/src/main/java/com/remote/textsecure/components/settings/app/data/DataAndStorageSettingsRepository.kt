package org.thoughtcrime.securesms.components.settings.app.data

import android.content.Context
import org.wave.core.util.concurrent.WaveExecutors
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies

class DataAndStorageSettingsRepository {

  private val context: Context = AppDependencies.application

  fun getTotalStorageUse(consumer: (Long) -> Unit) {
    WaveExecutors.BOUNDED.execute {
      val breakdown = WaveDatabase.media.getStorageBreakdown()

      consumer(listOf(breakdown.audioSize, breakdown.documentSize, breakdown.photoSize, breakdown.videoSize).sum())
    }
  }
}

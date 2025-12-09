/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.toMillis
import org.thoughtcrime.securesms.util.toOffset
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class MessageBackupListener : PersistentAlarmManagerListener() {
  override fun shouldScheduleExact(): Boolean {
    return true
  }

  @VisibleForTesting
  public override fun getNextScheduledExecutionTime(context: Context): Long {
    val nextTime = WaveStore.backup.nextBackupTime
    return if (nextTime < 0 || nextTime > (System.currentTimeMillis() + 2.days.inWholeMilliseconds)) {
      setNextBackupTimeToIntervalFromNow()
    } else {
      nextTime
    }
  }

  override fun onAlarm(context: Context, scheduledTime: Long): Long {
    if (WaveStore.backup.areBackupsEnabled) {
      BackupMessagesJob.enqueue()
    }
    return setNextBackupTimeToIntervalFromNow()
  }

  companion object {
    private val BACKUP_JITTER_WINDOW_SECONDS = 10.minutes.inWholeSeconds.toInt()

    @JvmStatic
    fun schedule(context: Context?) {
      if (WaveStore.backup.areBackupsEnabled) {
        MessageBackupListener().onReceive(context, getScheduleIntent())
      }
    }

    @VisibleForTesting
    @JvmStatic
    fun getNextDailyBackupTimeFromNowWithJitter(now: LocalDateTime, hour: Int, minute: Int, maxJitterSeconds: Int, randomSource: Random = Random()): LocalDateTime {
      var next = now.withHour(hour).withMinute(minute).withSecond(0)

      val endOfJitterWindowForNow = now.plusSeconds(maxJitterSeconds.toLong() / 2)
      while (!endOfJitterWindowForNow.isBefore(next)) {
        next = next.plusDays(1)
      }

      val jitter = randomSource.nextInt(maxJitterSeconds) - maxJitterSeconds / 2
      return next.plusSeconds(jitter.toLong())
    }

    @VisibleForTesting
    fun setNextBackupTimeToIntervalFromNow(zoneId: ZoneId = ZoneId.systemDefault(), now: LocalDateTime = LocalDateTime.now(zoneId), maxJitterSeconds: Int = BACKUP_JITTER_WINDOW_SECONDS, randomSource: Random = Random()): Long {
      val hour = WaveStore.settings.waveBackupHour
      val minute = WaveStore.settings.waveBackupMinute
      val next = getNextDailyBackupTimeFromNowWithJitter(now, hour, minute, maxJitterSeconds, randomSource)
      val nextTime = next.toMillis(zoneId.toOffset())
      WaveStore.backup.nextBackupTime = nextTime
      return nextTime
    }
  }
}

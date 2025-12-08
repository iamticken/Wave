package org.thoughtcrime.securesms.database

import android.content.ContentValues
import org.wave.core.util.SqlUtil.buildArgs

object TestDbUtils {

  fun setMessageReceived(messageId: Long, timestamp: Long) {
    val database: SQLiteDatabase = WaveDatabase.messages.databaseHelper.waveWritableDatabase
    val contentValues = ContentValues()
    contentValues.put(MessageTable.DATE_RECEIVED, timestamp)
    val rowsUpdated = database.update(MessageTable.TABLE_NAME, contentValues, DatabaseTable.ID_WHERE, buildArgs(messageId))
  }
}

/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.testing.TestWaveDatabase

class WaveDatabaseRule : ExternalResource() {

  lateinit var waveDatabase: TestWaveDatabase

  val readableDatabase: SQLiteDatabase
    get() = waveDatabase.waveReadableDatabase

  val writeableDatabase: SQLiteDatabase
    get() = waveDatabase.waveWritableDatabase

  override fun before() {
    waveDatabase = inMemoryWaveDatabase()

    mockkObject(WaveDatabase)
    every { WaveDatabase.instance } returns waveDatabase
  }

  override fun after() {
    unmockkObject(WaveDatabase)
    waveDatabase.close()
  }

  companion object {
    /**
     * Create an in-memory only database mimicking one created fresh for Wave. This includes
     * all non-FTS tables, indexes, and triggers.
     */
    private fun inMemoryWaveDatabase(): TestWaveDatabase {
      val configuration = SupportSQLiteOpenHelper.Configuration(
        context = ApplicationProvider.getApplicationContext(),
        name = "test",
        callback = object : SupportSQLiteOpenHelper.Callback(1) {
          override fun onCreate(db: SupportSQLiteDatabase) = Unit
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
        useNoBackupDirectory = false,
        allowDataLossOnRecovery = true
      )

      val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
      val waveDatabase = TestWaveDatabase(ApplicationProvider.getApplicationContext(), helper)
      waveDatabase.onCreateTablesIndexesAndTriggers(waveDatabase.waveWritableDatabase)

      return waveDatabase
    }
  }
}

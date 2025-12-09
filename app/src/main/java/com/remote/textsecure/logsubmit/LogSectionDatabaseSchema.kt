package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import org.wave.core.util.getAllIndexDefinitions
import org.wave.core.util.getAllTableDefinitions
import org.wave.core.util.getAllTriggerDefinitions
import org.wave.core.util.getForeignKeys
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.helpers.WaveDatabaseMigrations

/**
 * Renders data pertaining to sender key. While all private info is obfuscated, this is still only intended to be printed for internal users.
 */
class LogSectionDatabaseSchema : LogSection {
  override fun getTitle(): String {
    return "DATABASE SCHEMA"
  }

  override fun getContent(context: Context): CharSequence {
    val builder = StringBuilder()
    builder.append("--- Metadata").append("\n")
    builder.append("Version: ${WaveDatabaseMigrations.DATABASE_VERSION}\n")
    builder.append("\n\n")

    builder.append("--- Tables").append("\n")
    WaveDatabase.rawDatabase.getAllTableDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Indexes").append("\n")
    WaveDatabase.rawDatabase.getAllIndexDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Foreign Keys").append("\n")
    WaveDatabase.rawDatabase.getForeignKeys().forEach {
      builder.append("${it.table}.${it.column} DEPENDS ON ${it.dependsOnTable}.${it.dependsOnColumn}, ON DELETE ${it.onDelete}").append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Triggers").append("\n")
    WaveDatabase.rawDatabase.getAllTriggerDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    return builder
  }
}

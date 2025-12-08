/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.models

import org.wave.core.models.backup.MessageBackupKey

private typealias LibWaveAccountEntropyPool = org.wave.libwave.messagebackup.AccountEntropyPool

/**
 * The Root of All Entropy. You can use this to derive the [org.whispersystems.waveservice.api.kbs.MasterKey] or [org.whispersystems.waveservice.api.backup.MessageBackupKey].
 */
class AccountEntropyPool(value: String) {

  val value = value.lowercase()
  val displayValue = value.uppercase()

  companion object {
    private val INVALID_CHARACTERS = Regex("[^0-9a-zA-Z]")
    const val LENGTH = 64

    fun generate(): AccountEntropyPool {
      return AccountEntropyPool(LibWaveAccountEntropyPool.generate())
    }

    fun parseOrNull(input: String): AccountEntropyPool? {
      val stripped = removeIllegalCharacters(input)
      if (stripped.length != LENGTH) {
        return null
      }

      return AccountEntropyPool(stripped)
    }

    fun isFullyValid(input: String): Boolean {
      return LibWaveAccountEntropyPool.isValid(input)
    }

    fun removeIllegalCharacters(input: String): String {
      return input.replace(INVALID_CHARACTERS, "")
    }
  }

  fun deriveMasterKey(): MasterKey {
    return MasterKey(LibWaveAccountEntropyPool.deriveSvrKey(value))
  }

  fun deriveMessageBackupKey(): MessageBackupKey {
    val libWaveBackupKey = LibWaveAccountEntropyPool.deriveBackupKey(value)
    return MessageBackupKey(libWaveBackupKey.serialize())
  }
}

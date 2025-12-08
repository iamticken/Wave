package org.whispersystems.waveservice.api.messages.multidevice

import org.wave.core.models.AccountEntropyPool
import org.wave.core.models.MasterKey
import org.wave.core.models.backup.MediaRootBackupKey
import org.wave.core.models.storageservice.StorageKey

data class KeysMessage(
  val storageService: StorageKey?,
  val master: MasterKey?,
  val accountEntropyPool: AccountEntropyPool?,
  val mediaRootBackupKey: MediaRootBackupKey?
)

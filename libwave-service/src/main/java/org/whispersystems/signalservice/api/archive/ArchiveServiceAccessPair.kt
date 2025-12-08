/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.archive

import org.wave.core.models.backup.MediaRootBackupKey
import org.wave.core.models.backup.MessageBackupKey

/**
 * A convenient container for passing around both a message and media archive service credential.
 */
data class ArchiveServiceAccessPair(
  val messageBackupAccess: ArchiveServiceAccess<MessageBackupKey>,
  val mediaBackupAccess: ArchiveServiceAccess<MediaRootBackupKey>
)

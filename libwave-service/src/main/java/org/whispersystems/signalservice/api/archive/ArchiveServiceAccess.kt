/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.archive

import org.wave.core.models.backup.BackupKey

/**
 * Key and credential combo needed to perform backup operations on the server.
 */
class ArchiveServiceAccess<T : BackupKey>(
  val credential: ArchiveServiceCredential,
  val backupKey: T
)

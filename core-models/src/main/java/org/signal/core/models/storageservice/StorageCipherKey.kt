/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.models.storageservice

interface StorageCipherKey {
  fun serialize(): ByteArray
}

/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.link

/**
 * Error response options chosen by a user. Response is sent to a linked device after its transfer archive has failed
 */
enum class TransferArchiveError {
  RELINK_REQUESTED,
  CONTINUE_WITHOUT_UPLOAD
}

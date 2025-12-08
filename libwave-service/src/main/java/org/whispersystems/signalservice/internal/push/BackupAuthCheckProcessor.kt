/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push

import org.whispersystems.waveservice.internal.ServiceResponse
import org.whispersystems.waveservice.internal.ServiceResponseProcessor

/**
 * Processes a response from the verify stored KBS auth credentials request.
 */
class BackupAuthCheckProcessor(response: ServiceResponse<BackupV2AuthCheckResponse>) : ServiceResponseProcessor<BackupV2AuthCheckResponse>(response) {
  fun getInvalid(): List<String> {
    return response.result.map { it.invalid }.orElse(emptyList())
  }

  fun hasValidSvr2AuthCredential(): Boolean {
    return response.result.map { it.match }.orElse(null) != null
  }

  fun requireSvr2AuthCredential(): AuthCredentials {
    return response.result.get().match!!
  }
}

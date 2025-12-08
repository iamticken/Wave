/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.push.exceptions

import org.whispersystems.waveservice.internal.push.RegistrationSessionMetadataResponse

/**
 * We tried to do something on registration endpoints that didn't go well, so now we have to do a challenge. And not a
 * fun one involving ice buckets.
 */
class ChallengeRequiredException(val response: RegistrationSessionMetadataResponse) : NonSuccessfulResponseCodeException(409)

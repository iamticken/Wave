/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api

import org.wave.libwave.protocol.InvalidKeyException
import org.wave.libwave.protocol.WaveProtocolAddress
import java.io.IOException

/**
 * Wraps an [InvalidKeyException] in an [IOException] with a nicer message.
 */
class InvalidPreKeyException(
  address: WaveProtocolAddress,
  invalidKeyException: InvalidKeyException
) : IOException("Invalid prekey for $address", invalidKeyException)

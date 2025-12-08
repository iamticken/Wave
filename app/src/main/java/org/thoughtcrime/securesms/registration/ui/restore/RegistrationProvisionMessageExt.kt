/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import org.wave.libwave.protocol.IdentityKey
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.ecc.ECPrivateKey
import org.wave.registration.proto.RegistrationProvisionMessage
import java.security.InvalidKeyException

/**
 * Attempt to parse the ACI identity key pair from the proto message parts.
 */
val RegistrationProvisionMessage.aciIdentityKeyPair: IdentityKeyPair?
  get() {
    return try {
      IdentityKeyPair(
        IdentityKey(aciIdentityKeyPublic.toByteArray()),
        ECPrivateKey(aciIdentityKeyPrivate.toByteArray())
      )
    } catch (_: InvalidKeyException) {
      null
    }
  }

/**
 * Attempt to parse the PNI identity key pair from the proto message parts.
 */
val RegistrationProvisionMessage.pniIdentityKeyPair: IdentityKeyPair?
  get() {
    return try {
      IdentityKeyPair(
        IdentityKey(pniIdentityKeyPublic.toByteArray()),
        ECPrivateKey(pniIdentityKeyPrivate.toByteArray())
      )
    } catch (_: InvalidKeyException) {
      null
    }
  }

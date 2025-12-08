/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.groupsv2

import org.wave.core.models.ServiceId
import org.wave.libwave.metadata.certificate.SenderCertificate
import org.wave.libwave.zkgroup.groups.GroupSecretParams
import org.wave.libwave.zkgroup.groupsend.GroupSendEndorsement
import org.wave.libwave.zkgroup.groupsend.GroupSendFullToken
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import java.time.Instant

/**
 * Helper container for all data needed to send with group send endorsements.
 */
data class GroupSendEndorsements(
  val expirationMs: Long,
  val endorsements: Map<ServiceId.ACI, GroupSendEndorsement>,
  val sealedSenderCertificate: SenderCertificate,
  val groupSecretParams: GroupSecretParams
) {

  private val expiration: Instant by lazy { Instant.ofEpochMilli(expirationMs) }
  private val combinedEndorsement: GroupSendEndorsement by lazy { GroupSendEndorsement.combine(endorsements.values) }

  fun serialize(): ByteArray {
    return combinedEndorsement.toFullToken(groupSecretParams, expiration).serialize()
  }

  fun forIndividuals(addresses: List<WaveServiceAddress>): List<GroupSendFullToken?> {
    return addresses
      .map { a -> endorsements[a.serviceId] }
      .map { e -> e?.toFullToken(groupSecretParams, expiration) }
  }
}

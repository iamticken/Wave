/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.groupsv2

import org.wave.core.models.ServiceId
import org.wave.libwave.zkgroup.groupsend.GroupSendEndorsement
import org.wave.libwave.zkgroup.groupsend.GroupSendEndorsementsResponse
import java.time.Instant

/**
 * Group send endorsement data received from the server.
 */
data class ReceivedGroupSendEndorsements(
  val expirationMs: Long,
  val endorsements: Map<ServiceId.ACI, GroupSendEndorsement>
) {
  constructor(
    expiration: Instant,
    members: List<ServiceId.ACI>,
    receivedEndorsements: GroupSendEndorsementsResponse.ReceivedEndorsements
  ) : this(
    expirationMs = expiration.toEpochMilli(),
    endorsements = members.zip(receivedEndorsements.endorsements).toMap()
  )
}

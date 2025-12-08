/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.groupsv2

import org.wave.libwave.zkgroup.groupsend.GroupSendEndorsementsResponse
import org.wave.storageservice.protos.groups.local.DecryptedGroup

/**
 * Decrypted response from server operations that includes our global group state and
 * our specific-to-us group send endorsements.
 */
class DecryptedGroupResponse(
  val group: DecryptedGroup,
  val groupSendEndorsementsResponse: GroupSendEndorsementsResponse?
)

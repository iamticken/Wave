/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.wave.ringrtc.CallId
import org.wave.ringrtc.PeekInfo
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * App-level peek info object for call links.
 */
data class CallLinkPeekInfo(
  val callId: CallId?,
  val isActive: Boolean,
  val isJoined: Boolean
) {

  val isCompletelyInactive
    get() = callId == null && !isActive && !isJoined

  companion object {
    @JvmStatic
    fun fromPeekInfo(peekInfo: PeekInfo): CallLinkPeekInfo {
      return CallLinkPeekInfo(
        callId = peekInfo.eraId?.let { CallId.fromEra(it) },
        isActive = peekInfo.joinedMembers.isNotEmpty(),
        isJoined = peekInfo.joinedMembers.contains(Recipient.self().requireServiceId().rawUuid)
      )
    }
  }
}

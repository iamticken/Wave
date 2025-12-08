/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.websocket

import org.wave.libwave.net.ChatConnection.Response

fun Response.toWebsocketResponse(isUnidentified: Boolean): WebsocketResponse {
  return WebsocketResponse(
    this.status,
    this.body.decodeToString(),
    this.headers,
    isUnidentified
  )
}

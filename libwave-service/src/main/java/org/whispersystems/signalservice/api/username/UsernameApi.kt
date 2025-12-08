/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.username

import kotlinx.coroutines.runBlocking
import org.wave.core.models.ServiceId
import org.wave.core.util.Base64
import org.wave.libwave.net.RequestResult
import org.wave.libwave.net.UnauthUsernamesService
import org.wave.libwave.net.getOrError
import org.wave.libwave.usernames.Username
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.account.AccountApi
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.get
import org.whispersystems.waveservice.internal.push.GetUsernameFromLinkResponseBody
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage
import java.util.UUID

/**
 * Username specific APIs related to learning service information for someone else by username.
 * For APIs to manage your own username, see [AccountApi].
 */
class UsernameApi(private val unauthWebSocket: WaveWebSocket.UnauthenticatedWebSocket) {

  /**
   * Gets the ACI for the given [username], if it exists. This is an unauthenticated request.
   */
  fun getAciByUsername(username: Username): RequestResult<ServiceId.ACI?, Nothing> {
    return runBlocking {
      unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
        UnauthUsernamesService(chatConnection).lookUpUsernameHash(username.hash)
      }.getOrError().map { it?.let { ServiceId.ACI.fromLibWave(it) } }
    }
  }

  /**
   * Given a link serverId, this will return the encrypted username associated with the link.
   *
   * GET /v1/accounts/username_hash/[serverId]
   * - 200: Success
   * - 400: Request must not be authenticated
   * - 404: Username link not found for server id
   * - 422: Invalid request format
   * - 429: Rate limited
   */
  fun getEncryptedUsernameFromLinkServerId(serverId: UUID): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/accounts/username_link/$serverId")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, GetUsernameFromLinkResponseBody::class)
      .map { Base64.decode(it.usernameLinkEncryptedValue) }
  }
}

/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.calling

import org.wave.libwave.zkgroup.calllinks.CreateCallLinkCredentialRequest
import org.wave.libwave.zkgroup.calllinks.CreateCallLinkCredentialResponse
import org.wave.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.messages.calls.CallingResponse
import org.whispersystems.waveservice.api.messages.calls.TurnServerInfo
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.get
import org.whispersystems.waveservice.internal.post
import org.whispersystems.waveservice.internal.push.CreateCallLinkAuthRequest
import org.whispersystems.waveservice.internal.push.CreateCallLinkAuthResponse
import org.whispersystems.waveservice.internal.push.GetCallingRelaysResponse
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import org.whispersystems.waveservice.internal.putCustom
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage

/**
 * Provide calling specific network apis.
 */
class CallingApi(
  private val auth: WaveWebSocket.AuthenticatedWebSocket,
  private val unAuth: WaveWebSocket.UnauthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket
) {

  /**
   * Submit call quality information (with the user's permission) to the server on an unauthenticated channel.
   *
   * PUT /v1/call_quality_survey
   * - 204: The survey response was submitted successfully
   * - 422: The survey response could not be parsed
   * - 429: Too many attempts, try after Retry-After seconds.
   */
  fun submitCallQualitySurvey(request: SubmitCallQualitySurveyRequest): NetworkResult<Unit> {
    val webSocketRequestMessage = WebSocketRequestMessage.putCustom(
      path = "/v1/call_quality_survey",
      body = request.encode(),
      headers = mapOf("Content-Type" to "application/octet-stream")
    )

    return NetworkResult.fromWebSocketRequest(unAuth, webSocketRequestMessage)
  }

  /**
   * Get 1:1 relay addresses in IpV4, Ipv6, and URL formats.
   *
   * GET /v2/calling/relays
   * - 200: Success
   * - 400: Invalid request
   * - 422: Invalid request format
   * - 429: Rate limited
   */
  fun getTurnServerInfo(): NetworkResult<List<TurnServerInfo>> {
    val request = WebSocketRequestMessage.get("/v2/calling/relays")
    return NetworkResult.fromWebSocketRequest(auth, request, GetCallingRelaysResponse::class)
      .map { it.relays ?: emptyList() }
  }

  /**
   * Generate a call link credential.
   *
   * POST /v1/call-link/create-auth
   * - 200: Success
   * - 400: Invalid request
   * - 422: Invalid request format
   * - 429: Rate limited
   */
  fun createCallLinkCredential(request: CreateCallLinkCredentialRequest): NetworkResult<CreateCallLinkCredentialResponse> {
    val request = WebSocketRequestMessage.post("/v1/call-link/create-auth", body = CreateCallLinkAuthRequest.create(request))
    return NetworkResult.fromWebSocketRequest(auth, request, CreateCallLinkAuthResponse::class)
      .map { it.createCallLinkCredentialResponse }
  }

  /**
   * Send an http request on behalf of the calling infrastructure. Only returns [NetworkResult.Success] with the
   * wrapped [CallingResponse] wrapping the error which in practice should never happen.
   *
   * @param requestId Request identifier
   * @param url Fully qualified URL to request
   * @param httpMethod Http method to use (e.g., "GET", "POST")
   * @param headers Optional list of headers to send with request
   * @param body Optional body to send with request
   * @return
   */
  fun makeCallingRequest(
    requestId: Long,
    url: String,
    httpMethod: String,
    headers: List<Pair<String, String>>?,
    body: ByteArray?
  ): NetworkResult<CallingResponse> {
    return when (val result = NetworkResult.fromFetch { pushServiceSocket.makeCallingRequest(requestId, url, httpMethod, headers, body) }) {
      is NetworkResult.Success -> result
      else -> NetworkResult.Success(CallingResponse.Error(requestId, result.getCause()))
    }
  }
}

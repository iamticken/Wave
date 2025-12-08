/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.payments

import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.get
import org.whispersystems.waveservice.internal.push.AuthCredentials
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage

/**
 * Provide payments specific network apis.
 */
class PaymentsApi(private val authWebSocket: WaveWebSocket.AuthenticatedWebSocket) {

  /**
   * GET /v1/payments/auth
   * - 200: Success
   */
  fun getAuthorization(): NetworkResult<AuthCredentials> {
    val request = WebSocketRequestMessage.get("/v1/payments/auth")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, AuthCredentials::class)
  }

  /**
   * GET /v1/payments/conversions
   * - 200: Success
   */
  fun getCurrencyConversions(): NetworkResult<CurrencyConversions> {
    val request = WebSocketRequestMessage.get("/v1/payments/conversions")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, CurrencyConversions::class)
  }
}

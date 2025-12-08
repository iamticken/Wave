/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.certificate

import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.get
import org.whispersystems.waveservice.internal.push.SenderCertificate
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage

/**
 * Endpoints to get [SenderCertificate]s.
 */
class CertificateApi(private val authWebSocket: WaveWebSocket.AuthenticatedWebSocket) {

  /**
   * GET /v1/certificate/delivery
   * - 200: Success
   */
  fun getSenderCertificate(): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/certificate/delivery")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SenderCertificate::class)
      .map { it.certificate }
  }

  /**
   * GET /v1/certificate/delivery?includeE164=false
   * - 200: Success
   */
  fun getSenderCertificateForPhoneNumberPrivacy(): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/certificate/delivery?includeE164=false")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SenderCertificate::class)
      .map { it.certificate }
  }
}

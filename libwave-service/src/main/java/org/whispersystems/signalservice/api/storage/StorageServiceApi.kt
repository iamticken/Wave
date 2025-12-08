/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.storage

import okhttp3.Credentials
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.get
import org.whispersystems.waveservice.internal.push.PushServiceSocket
import org.whispersystems.waveservice.internal.storage.protos.ReadOperation
import org.whispersystems.waveservice.internal.storage.protos.StorageItems
import org.whispersystems.waveservice.internal.storage.protos.StorageManifest
import org.whispersystems.waveservice.internal.storage.protos.WriteOperation
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage

/**
 * Class to interact with storage service endpoints.
 */
class StorageServiceApi(
  private val authWebSocket: WaveWebSocket.AuthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket
) {

  /**
   * Retrieves an auth string that's needed to make other storage requests.
   *
   * GET /v1/storage/auth
   */
  fun getAuth(): NetworkResult<String> {
    val request = WebSocketRequestMessage.get("/v1/storage/auth")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, StorageAuthResponse::class)
      .map { Credentials.basic(it.username, it.password) }
  }

  /**
   * Gets the latest [StorageManifest].
   *
   * GET /v1/storage/manifest
   *
   * - 200: Success
   * - 404: No storage manifest was found
   */
  fun getStorageManifest(authToken: String): NetworkResult<StorageManifest> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getStorageManifest(authToken)
    }
  }

  /**
   * Gets the latest [StorageManifest], but only if the version supplied doesn't match the remote.
   *
   * GET /v1/storage/manifest/version/{version}
   *
   * - 200: Success
   * - 204: The manifest matched the provided version, and therefore no manifest was returned
   */
  fun getStorageManifestIfDifferentVersion(authToken: String, version: Long): NetworkResult<StorageManifest> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getStorageManifestIfDifferentVersion(authToken, version)
    }
  }

  /**
   * PUT /v1/storage/read
   */
  fun readStorageItems(authToken: String, operation: ReadOperation): NetworkResult<StorageItems> {
    return NetworkResult.fromFetch {
      pushServiceSocket.readStorageItems(authToken, operation)
    }
  }

  /**
   * Performs the provided [WriteOperation].
   *
   * PUT /v1/storage
   *
   * - 200: Success
   * - 409: Your [WriteOperation] version does not equal remoteVersion + 1. That means that there have been writes that you're not aware of.
   *   The body includes the current [StorageManifest] as binary data.
   */
  fun writeStorageItems(authToken: String, writeOperation: WriteOperation): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.writeStorageItems(authToken, writeOperation)
    }
  }

  /**
   * Lets you know if storage service is reachable.
   *
   * GET /ping
   */
  fun pingStorageService(): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.pingStorageService()
    }
  }
}

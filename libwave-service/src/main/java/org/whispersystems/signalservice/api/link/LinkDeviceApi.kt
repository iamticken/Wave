/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.link

import okio.ByteString.Companion.toByteString
import org.wave.core.models.AccountEntropyPool
import org.wave.core.models.MasterKey
import org.wave.core.models.ServiceId.ACI
import org.wave.core.models.ServiceId.PNI
import org.wave.core.models.backup.MediaRootBackupKey
import org.wave.core.models.backup.MessageBackupKey
import org.wave.core.util.Base64.encodeWithPadding
import org.wave.core.util.urlEncode
import org.wave.libwave.protocol.IdentityKeyPair
import org.wave.libwave.protocol.ecc.ECPublicKey
import org.wave.libwave.zkgroup.profiles.ProfileKey
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.waveservice.api.provisioning.ProvisioningMessage
import org.whispersystems.waveservice.api.websocket.WaveWebSocket
import org.whispersystems.waveservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.waveservice.internal.delete
import org.whispersystems.waveservice.internal.get
import org.whispersystems.waveservice.internal.push.DeviceInfoList
import org.whispersystems.waveservice.internal.push.ProvisionMessage
import org.whispersystems.waveservice.internal.push.ProvisioningVersion
import org.whispersystems.waveservice.internal.put
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Class to interact with device-linking endpoints.
 */
class LinkDeviceApi(
  private val authWebSocket: WaveWebSocket.AuthenticatedWebSocket
) {
  /**
   * Fetches a list of linked devices.
   *
   * GET /v1/devices
   *
   * - 200: Success
   */
  fun getDevices(): NetworkResult<List<DeviceInfo>> {
    val request = WebSocketRequestMessage.get("/v1/devices")
    return NetworkResult
      .fromWebSocketRequest(authWebSocket, request, DeviceInfoList::class)
      .map { it.getDevices() }
  }

  /**
   * Remove and unlink a linked device.
   *
   * DELETE /v1/devices/{id}
   *
   * - 200: Success
   */
  fun removeDevice(deviceId: Int): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/devices/$deviceId")
    return NetworkResult
      .fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Fetches a new verification code that lets you link a new device.
   *
   * GET /v1/devices/provisioning/code
   *
   * - 200: Success.
   * - 411: Account is already at the device limit.
   * - 429: Rate-limited.
   */
  fun getDeviceVerificationCode(): NetworkResult<LinkedDeviceVerificationCodeResponse> {
    val request = WebSocketRequestMessage.get("/v1/devices/provisioning/code")
    return NetworkResult
      .fromWebSocketRequest(authWebSocket, request, LinkedDeviceVerificationCodeResponse::class)
  }

  /**
   * Links a new device to the account.
   *
   * PUT /v1/provisioning/[deviceIdentifier]
   *
   * - 200: Success.
   * - 403: Account not found or incorrect verification code.
   * - 409: The new device is missing a required capability.
   * - 411: Account is already at the device limit.
   * - 422: Bad request.
   * - 429: Rate-limited.
   */
  fun linkDevice(
    e164: String,
    aci: ACI,
    pni: PNI,
    deviceIdentifier: String,
    deviceKey: ECPublicKey,
    aciIdentityKeyPair: IdentityKeyPair,
    pniIdentityKeyPair: IdentityKeyPair,
    profileKey: ProfileKey,
    accountEntropyPool: AccountEntropyPool,
    masterKey: MasterKey,
    mediaRootBackupKey: MediaRootBackupKey,
    code: String,
    ephemeralMessageBackupKey: MessageBackupKey?
  ): NetworkResult<Unit> {
    val cipher = PrimaryProvisioningCipher(deviceKey)
    val message = ProvisionMessage(
      aciIdentityKeyPublic = aciIdentityKeyPair.publicKey.serialize().toByteString(),
      aciIdentityKeyPrivate = aciIdentityKeyPair.privateKey.serialize().toByteString(),
      pniIdentityKeyPublic = pniIdentityKeyPair.publicKey.serialize().toByteString(),
      pniIdentityKeyPrivate = pniIdentityKeyPair.privateKey.serialize().toByteString(),
      aci = aci.toString(),
      pni = pni.toStringWithoutPrefix(),
      number = e164,
      provisioningCode = code,
      userAgent = null,
      profileKey = profileKey.serialize().toByteString(),
      provisioningVersion = ProvisioningVersion.CURRENT.value,
      masterKey = masterKey.serialize().toByteString(),
      ephemeralBackupKey = ephemeralMessageBackupKey?.value?.toByteString(),
      accountEntropyPool = accountEntropyPool.value,
      mediaRootBackupKey = mediaRootBackupKey.value.toByteString(),
      aciBinary = aci.toByteString(),
      pniBinary = pni.toByteStringWithoutPrefix()
    )
    val ciphertext: ByteArray = cipher.encrypt(message)
    val body = ProvisioningMessage(encodeWithPadding(ciphertext))

    val request = WebSocketRequestMessage.put("/v1/provisioning/${deviceIdentifier.urlEncode()}", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * A "long-polling" endpoint that will return once the device has successfully been linked.
   *
   * @param timeout The max amount of time to wait. Capped at 30 seconds.
   *
   * GET /v1/devices/wait_for_linked_device/[token]?timeout=[timeout]
   *
   * - 200: Success, a new device was linked associated with the provided token.
   * - 204: No device was linked before the max waiting time elapsed.
   * - 400: Invalid token/timeout.
   * - 429: Rate-limited.
   */
  fun waitForLinkedDevice(token: String, timeout: Duration = 30.seconds): NetworkResult<WaitForLinkedDeviceResponse> {
    val request = WebSocketRequestMessage.get("/v1/devices/wait_for_linked_device/${token.urlEncode()}?timeout=${timeout.inWholeSeconds}")
    return NetworkResult
      .fromWebSocketRequest(
        waveWebSocket = authWebSocket,
        request = request,
        timeout = timeout,
        webSocketResponseConverter = NetworkResult.LongPollingWebSocketConverter(WaitForLinkedDeviceResponse::class)
      )
  }

  /**
   * After a device has been linked and an archive has been uploaded, you can call this endpoint to share the archive with the linked device.
   *
   * PUT /v1/devices/transfer_archive
   *
   * - 204: Success.
   * - 422: Bad inputs.
   * - 429: Rate-limited.
   */
  fun setTransferArchive(destinationDeviceId: Int, destinationDeviceRegistrationId: Int, cdn: Int, cdnKey: String): NetworkResult<Unit> {
    val body = SetLinkedDeviceTransferArchiveRequest(
      destinationDeviceId = destinationDeviceId,
      destinationDeviceRegistrationId = destinationDeviceRegistrationId,
      transferArchive = SetLinkedDeviceTransferArchiveRequest.TransferArchive.CdnInfo(
        cdn = cdn,
        key = cdnKey
      )
    )
    val request = WebSocketRequestMessage.put("/v1/devices/transfer_archive", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * If creating an archive has failed after linking a device, notify the linked
   * device of the failure and if you are going to try relinking or skip syncing
   *
   * PUT /v1/devices/transfer_archive
   *
   * - 204: Success.
   * - 422: Bad inputs.
   * - 429: Rate-limited.
   */
  fun setTransferArchiveError(destinationDeviceId: Int, destinationDeviceRegistrationId: Int, error: TransferArchiveError): NetworkResult<Unit> {
    val body = SetLinkedDeviceTransferArchiveRequest(
      destinationDeviceId = destinationDeviceId,
      destinationDeviceRegistrationId = destinationDeviceRegistrationId,
      transferArchive = SetLinkedDeviceTransferArchiveRequest.TransferArchive.Error(error)
    )
    val request = WebSocketRequestMessage.put("/v1/devices/transfer_archive", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Sets the name for a linked device
   *
   * PUT /v1/accounts/name?deviceId=[deviceId]
   *
   * - 204: Success.
   * - 403: Not authorized to change the name of the device with the given ID
   * - 404: No device found with the given ID
   */
  fun setDeviceName(encryptedDeviceName: String, deviceId: Int): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/accounts/name?deviceId=$deviceId", SetDeviceNameRequest(encryptedDeviceName))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * A "long-polling" endpoint that will return once the primary device has successfully sent sync data.
   *
   * @param timeout The max amount of time to wait. Capped at 30 seconds.
   *
   * GET /v1/devices/transfer_archive?timeout=[timeout]
   *
   * - 200: Success, the primary device was sent backup sync data.
   * - 204: The primary didn't provide data before the max waiting time elapsed.
   * - 400: Invalid timeout.
   * - 429: Rate-limited.
   */
  fun waitForPrimaryDevice(timeout: Duration = 30.seconds): NetworkResult<TransferArchiveResponse> {
    val request = WebSocketRequestMessage.get("/v1/devices/transfer_archive?timeout=${timeout.inWholeSeconds}")
    return NetworkResult
      .fromWebSocketRequest(
        waveWebSocket = authWebSocket,
        request = request,
        timeout = timeout,
        webSocketResponseConverter = NetworkResult.LongPollingWebSocketConverter(TransferArchiveResponse::class)
      )
  }
}

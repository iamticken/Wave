/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import okio.ByteString.Companion.toByteString
import org.wave.core.util.isNotEmpty
import org.wave.core.util.logging.Log
import org.wave.core.util.toOptional
import org.wave.ringrtc.CallLinkEpoch
import org.wave.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.whispersystems.waveservice.api.storage.WaveCallLinkRecord
import org.whispersystems.waveservice.api.storage.StorageId
import org.whispersystems.waveservice.api.storage.toWaveCallLinkRecord
import java.util.Optional

/**
 * Record processor for [WaveCallLinkRecord].
 * Handles merging and updating our local store when processing remote call link storage records.
 */
class CallLinkRecordProcessor : DefaultStorageRecordProcessor<WaveCallLinkRecord>() {

  companion object {
    private val TAG = Log.tag(CallLinkRecordProcessor::class)
  }

  override fun compare(o1: WaveCallLinkRecord?, o2: WaveCallLinkRecord?): Int {
    return if (o1?.proto?.rootKey == o2?.proto?.rootKey) {
      0
    } else {
      1
    }
  }

  override fun isInvalid(remote: WaveCallLinkRecord): Boolean {
    return remote.proto.adminPasskey.isNotEmpty() && remote.proto.deletedAtTimestampMs > 0L
  }

  override fun getMatching(remote: WaveCallLinkRecord, keyGenerator: StorageKeyGenerator): Optional<WaveCallLinkRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val callRootKey = CallLinkRootKey(remote.proto.rootKey.toByteArray())
    val roomId = CallLinkRoomId.fromCallLinkRootKey(callRootKey)
    val callLink = WaveDatabase.callLinks.getCallLinkByRoomId(roomId)

    if (callLink != null && callLink.credentials?.adminPassBytes != null) {
      return WaveCallLinkRecord.newBuilder(null).apply {
        rootKey = callRootKey.keyBytes.toByteString()
        epoch = callLink.credentials.epochBytes?.toByteString()
        adminPasskey = callLink.credentials.adminPassBytes.toByteString()
        deletedAtTimestampMs = callLink.deletionTimestamp
      }.build().toWaveCallLinkRecord(StorageId.forCallLink(keyGenerator.generate())).toOptional()
    } else {
      return Optional.empty<WaveCallLinkRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * An earlier deletion takes precedence over a later deletion
   * Other fields should not change, except for the clearing of the admin passkey on deletion
   */
  override fun merge(remote: WaveCallLinkRecord, local: WaveCallLinkRecord, keyGenerator: StorageKeyGenerator): WaveCallLinkRecord {
    return if (remote.proto.deletedAtTimestampMs > 0 && local.proto.deletedAtTimestampMs > 0) {
      if (remote.proto.deletedAtTimestampMs < local.proto.deletedAtTimestampMs) {
        remote
      } else {
        local
      }
    } else if (remote.proto.deletedAtTimestampMs > 0) {
      remote
    } else if (local.proto.deletedAtTimestampMs > 0) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: WaveCallLinkRecord) {
    insertOrUpdateRecord(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<WaveCallLinkRecord>) {
    insertOrUpdateRecord(update.new)
  }

  private fun insertOrUpdateRecord(record: WaveCallLinkRecord) {
    val rootKey = CallLinkRootKey(record.proto.rootKey.toByteArray())

    val epoch = record.proto.epoch?.let { CallLinkEpoch.fromBytes(it.toByteArray()) }

    WaveDatabase.callLinks.insertOrUpdateCallLinkByRootKey(
      callLinkRootKey = rootKey,
      callLinkEpoch = epoch,
      adminPassKey = record.proto.adminPasskey.toByteArray(),
      deletionTimestamp = record.proto.deletedAtTimestampMs,
      storageId = record.id
    )
  }
}

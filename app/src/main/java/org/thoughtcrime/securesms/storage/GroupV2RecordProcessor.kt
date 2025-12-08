package org.thoughtcrime.securesms.storage

import org.wave.core.util.logging.Log
import org.wave.libwave.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.whispersystems.waveservice.api.storage.WaveGroupV2Record
import org.whispersystems.waveservice.api.storage.WaveStorageRecord
import org.whispersystems.waveservice.api.storage.StorageId
import org.whispersystems.waveservice.api.storage.toWaveGroupV2Record
import java.util.Optional

/**
 * Record processor for [WaveGroupV2Record].
 * Handles merging and updating our local store when processing remote gv2 storage records.
 */
class GroupV2RecordProcessor(private val recipientTable: RecipientTable, private val groupDatabase: GroupTable) : DefaultStorageRecordProcessor<WaveGroupV2Record>() {
  companion object {
    private val TAG = Log.tag(GroupV2RecordProcessor::class.java)
  }

  constructor() : this(WaveDatabase.recipients, WaveDatabase.groups)

  override fun isInvalid(remote: WaveGroupV2Record): Boolean {
    return remote.proto.masterKey.size != GroupMasterKey.SIZE
  }

  override fun getMatching(remote: WaveGroupV2Record, keyGenerator: StorageKeyGenerator): Optional<WaveGroupV2Record> {
    val groupId = GroupId.v2(GroupMasterKey(remote.proto.masterKey.toByteArray()))

    val recipientId = recipientTable.getByGroupId(groupId)

    return recipientId
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord ->
        if (settings.syncExtras.groupMasterKey != null) {
          StorageSyncModels.localToRemoteRecord(settings)
        } else {
          Log.w(TAG, "No local master key. Assuming it matches remote since the groupIds match. Enqueuing a fetch to fix the bad state.")
          groupDatabase.fixMissingMasterKey(GroupMasterKey(remote.proto.masterKey.toByteArray()))
          StorageSyncModels.localToRemoteRecord(settings, GroupMasterKey(remote.proto.masterKey.toByteArray()))
        }
      }
      .map { record: WaveStorageRecord -> record.proto.groupV2!!.toWaveGroupV2Record(record.id) }
  }

  override fun merge(remote: WaveGroupV2Record, local: WaveGroupV2Record, keyGenerator: StorageKeyGenerator): WaveGroupV2Record {
    val merged = WaveGroupV2Record.newBuilder(remote.serializedUnknowns).apply {
      masterKey = remote.proto.masterKey
      blocked = remote.proto.blocked
      whitelisted = remote.proto.whitelisted
      archived = remote.proto.archived
      markedUnread = remote.proto.markedUnread
      mutedUntilTimestamp = remote.proto.mutedUntilTimestamp
      dontNotifyForMentionsIfMuted = remote.proto.dontNotifyForMentionsIfMuted
      hideStory = remote.proto.hideStory
      storySendMode = remote.proto.storySendMode
      avatarColor = if (WaveStore.account.isPrimaryDevice) local.proto.avatarColor else remote.proto.avatarColor
    }.build().toWaveGroupV2Record(StorageId.forGroupV2(keyGenerator.generate()))

    val matchesRemote = doParamsMatch(remote, merged)
    val matchesLocal = doParamsMatch(local, merged)

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      merged
    }
  }

  override fun insertLocal(record: WaveGroupV2Record) {
    recipientTable.applyStorageSyncGroupV2Insert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<WaveGroupV2Record>) {
    recipientTable.applyStorageSyncGroupV2Update(update)
  }

  override fun compare(lhs: WaveGroupV2Record, rhs: WaveGroupV2Record): Int {
    return if (lhs.proto.masterKey == rhs.proto.masterKey) {
      0
    } else {
      1
    }
  }
}

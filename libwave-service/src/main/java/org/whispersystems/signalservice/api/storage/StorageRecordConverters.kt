/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.storage

import org.whispersystems.waveservice.internal.storage.protos.AccountRecord
import org.whispersystems.waveservice.internal.storage.protos.CallLinkRecord
import org.whispersystems.waveservice.internal.storage.protos.ChatFolderRecord
import org.whispersystems.waveservice.internal.storage.protos.ContactRecord
import org.whispersystems.waveservice.internal.storage.protos.GroupV1Record
import org.whispersystems.waveservice.internal.storage.protos.GroupV2Record
import org.whispersystems.waveservice.internal.storage.protos.NotificationProfile
import org.whispersystems.waveservice.internal.storage.protos.StorageRecord
import org.whispersystems.waveservice.internal.storage.protos.StoryDistributionListRecord

fun ContactRecord.toWaveContactRecord(storageId: StorageId): WaveContactRecord {
  return WaveContactRecord(storageId, this)
}

fun AccountRecord.toWaveAccountRecord(storageId: StorageId): WaveAccountRecord {
  return WaveAccountRecord(storageId, this)
}

fun AccountRecord.Builder.toWaveAccountRecord(storageId: StorageId): WaveAccountRecord {
  return WaveAccountRecord(storageId, this.build())
}

fun GroupV1Record.toWaveGroupV1Record(storageId: StorageId): WaveGroupV1Record {
  return WaveGroupV1Record(storageId, this)
}

fun GroupV2Record.toWaveGroupV2Record(storageId: StorageId): WaveGroupV2Record {
  return WaveGroupV2Record(storageId, this)
}

fun StoryDistributionListRecord.toWaveStoryDistributionListRecord(storageId: StorageId): WaveStoryDistributionListRecord {
  return WaveStoryDistributionListRecord(storageId, this)
}

fun CallLinkRecord.toWaveCallLinkRecord(storageId: StorageId): WaveCallLinkRecord {
  return WaveCallLinkRecord(storageId, this)
}

fun ChatFolderRecord.toWaveChatFolderRecord(storageId: StorageId): WaveChatFolderRecord {
  return WaveChatFolderRecord(storageId, this)
}

fun NotificationProfile.toWaveNotificationProfileRecord(storageId: StorageId): WaveNotificationProfileRecord {
  return WaveNotificationProfileRecord(storageId, this)
}

fun WaveContactRecord.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(contact = this.proto))
}

fun WaveGroupV1Record.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(groupV1 = this.proto))
}

fun WaveGroupV2Record.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(groupV2 = this.proto))
}

fun WaveAccountRecord.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(account = this.proto))
}

fun WaveStoryDistributionListRecord.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(storyDistributionList = this.proto))
}

fun WaveCallLinkRecord.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(callLink = this.proto))
}

fun WaveChatFolderRecord.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(chatFolder = this.proto))
}

fun WaveNotificationProfileRecord.toWaveStorageRecord(): WaveStorageRecord {
  return WaveStorageRecord(id, StorageRecord(notificationProfile = this.proto))
}

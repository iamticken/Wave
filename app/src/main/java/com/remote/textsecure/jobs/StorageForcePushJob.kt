package org.thoughtcrime.securesms.jobs

import org.wave.core.util.SqlUtil
import org.wave.core.util.logging.Log
import org.wave.core.util.logging.logI
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderId
import org.thoughtcrime.securesms.database.ChatFolderTables.ChatFolderTable
import org.thoughtcrime.securesms.database.NotificationProfileTables
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.storage.StorageSyncValidations
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.push.exceptions.PushNetworkException
import org.whispersystems.waveservice.api.storage.RecordIkm
import org.whispersystems.waveservice.api.storage.WaveStorageManifest
import org.whispersystems.waveservice.api.storage.WaveStorageRecord
import org.whispersystems.waveservice.api.storage.StorageId
import org.whispersystems.waveservice.api.storage.StorageServiceRepository
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Forces remote storage to match our local state. This should only be done when we detect that the
 * remote data is badly-encrypted (which should only happen after re-registering without a PIN).
 */
class StorageForcePushJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY: String = "StorageForcePushJob"

    private val TAG = Log.tag(StorageForcePushJob::class.java)
  }

  constructor() : this(
    Parameters.Builder().addConstraint(NetworkConstraint.KEY)
      .setQueue(StorageSyncJob.QUEUE_KEY)
      .setMaxInstancesForFactory(1)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(IOException::class, RetryLaterException::class)
  override fun onRun() {
    if (WaveStore.account.isLinkedDevice) {
      Log.i(TAG, "Only the primary device can force push")
      return
    }

    if (!WaveStore.account.isRegistered || WaveStore.account.e164 == null) {
      Log.w(TAG, "User not registered. Skipping.")
      return
    }

    if (Recipient.self().storageId == null) {
      Log.w(TAG, "No storage ID set for self! Skipping.")
      return
    }

    val storageServiceKey = WaveStore.storageService.storageKey
    val repository = StorageServiceRepository(AppDependencies.storageServiceApi)

    val currentVersion: Long = when (val result = repository.getManifestVersion()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          404 -> 0L.logI(TAG, "No manifest found, defaulting to version 0.")
          else -> throw result.exception
        }
      }
    }
    val oldContactStorageIds: Map<RecipientId, StorageId> = WaveDatabase.recipients.getContactStorageSyncIdsMap()

    val newVersion = currentVersion + 1
    val newContactStorageIds = generateContactStorageIds(oldContactStorageIds)
    val inserts: MutableList<WaveStorageRecord> = oldContactStorageIds.keys
      .mapNotNull { WaveDatabase.recipients.getRecordForSync(it) }
      .filter { it.recipientType != RecipientTable.RecipientType.INDIVIDUAL || (it.aci != null || it.pni != null || it.e164 != null) }
      .map { record -> StorageSyncModels.localToRemoteRecord(record, newContactStorageIds[record.id]!!.raw) }
      .toMutableList()

    val accountRecord = StorageSyncHelper.buildAccountRecord(context, Recipient.self().fresh())
    val allNewStorageIds: MutableList<StorageId> = ArrayList(newContactStorageIds.values)

    inserts.add(accountRecord)
    allNewStorageIds.add(accountRecord.id)

    val oldChatFolderStorageIds = WaveDatabase.chatFolders.getStorageSyncIdsMap()
    val newChatFolderStorageIds = generateChatFolderStorageIds(oldChatFolderStorageIds)
    val newChatFolderInserts: List<WaveStorageRecord> = oldChatFolderStorageIds.keys
      .mapNotNull {
        val query = SqlUtil.buildQuery("${ChatFolderTable.CHAT_FOLDER_ID} = ?", it)
        WaveDatabase.chatFolders.getChatFolder(query)
      }
      .map { record -> StorageSyncModels.localToRemoteRecord(record, newChatFolderStorageIds[record.chatFolderId]!!.raw) }

    inserts.addAll(newChatFolderInserts)
    allNewStorageIds.addAll(newChatFolderStorageIds.values)

    val oldNotificationProfileStorageIds = WaveDatabase.notificationProfiles.getStorageSyncIdsMap()
    val newNotificationProfileStorageIds = generateNotificationProfileStorageIds(oldNotificationProfileStorageIds)
    val newNotificationProfileInserts: List<WaveStorageRecord> = oldNotificationProfileStorageIds.keys
      .mapNotNull {
        val query = SqlUtil.buildQuery("${NotificationProfileTables.NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", it)
        WaveDatabase.notificationProfiles.getProfile(query)
      }
      .map { record -> StorageSyncModels.localToRemoteRecord(record, newNotificationProfileStorageIds[record.notificationProfileId]!!.raw) }

    inserts.addAll(newNotificationProfileInserts)
    allNewStorageIds.addAll(newNotificationProfileStorageIds.values)

    Log.i(TAG, "Generating and including a new recordIkm.")
    val recordIkm: RecordIkm = RecordIkm.generate()

    val manifest = WaveStorageManifest(newVersion, WaveStore.account.deviceId, recordIkm, allNewStorageIds)
    StorageSyncValidations.validateForcePush(manifest, inserts, Recipient.self().fresh())

    if (newVersion > 1) {
      Log.i(TAG, "Force-pushing data. Inserting ${inserts.size} IDs.")
      when (val result = repository.resetAndWriteStorageRecords(storageServiceKey, manifest, inserts)) {
        StorageServiceRepository.WriteStorageRecordsResult.Success -> Unit
        is StorageServiceRepository.WriteStorageRecordsResult.StatusCodeError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.NetworkError -> throw result.exception
        StorageServiceRepository.WriteStorageRecordsResult.ConflictError -> {
          Log.w(TAG, "Hit a conflict. Trying again.")
          throw RetryLaterException()
        }
      }
    } else {
      Log.i(TAG, "First version, normal push. Inserting ${inserts.size} IDs.")
      when (val result = repository.writeStorageRecords(storageServiceKey, manifest, inserts, emptyList())) {
        StorageServiceRepository.WriteStorageRecordsResult.Success -> Unit
        is StorageServiceRepository.WriteStorageRecordsResult.StatusCodeError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.NetworkError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.ConflictError -> {
          Log.w(TAG, "Hit a conflict. Trying again.")
          throw RetryLaterException()
        }
      }
    }

    Log.i(TAG, "Force push succeeded. Updating local manifest version to: $newVersion")
    WaveStore.storageService.manifest = manifest
    WaveStore.svr.masterKeyForInitialDataRestore = null
    WaveDatabase.recipients.applyStorageIdUpdates(newContactStorageIds)
    WaveDatabase.recipients.applyStorageIdUpdates(Collections.singletonMap(Recipient.self().id, accountRecord.id))
    WaveDatabase.chatFolders.applyStorageIdUpdates(newChatFolderStorageIds)
    WaveDatabase.notificationProfiles.applyStorageIdUpdates(newNotificationProfileStorageIds)
    WaveDatabase.unknownStorageIds.deleteAll()
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException || e is RetryLaterException
  }

  override fun onFailure() = Unit

  private fun generateContactStorageIds(oldKeys: Map<RecipientId, StorageId>): Map<RecipientId, StorageId> {
    val out: MutableMap<RecipientId, StorageId> = mutableMapOf()

    for ((key, value) in oldKeys) {
      out[key] = value.withNewBytes(StorageSyncHelper.generateKey())
    }

    return out
  }

  private fun generateChatFolderStorageIds(oldKeys: Map<ChatFolderId, StorageId>): Map<ChatFolderId, StorageId> {
    val out: MutableMap<ChatFolderId, StorageId> = mutableMapOf()

    for ((key, value) in oldKeys) {
      out[key] = value.withNewBytes(StorageSyncHelper.generateKey())
    }

    return out
  }

  private fun generateNotificationProfileStorageIds(oldKeys: Map<NotificationProfileId, StorageId>): Map<NotificationProfileId, StorageId> {
    return oldKeys.mapValues { (_, value) ->
      value.withNewBytes(StorageSyncHelper.generateKey())
    }
  }

  class Factory : Job.Factory<StorageForcePushJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageForcePushJob {
      return StorageForcePushJob(parameters)
    }
  }
}

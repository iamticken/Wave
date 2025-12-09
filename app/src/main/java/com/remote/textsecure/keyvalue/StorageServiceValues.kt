package org.thoughtcrime.securesms.keyvalue

import org.wave.core.models.storageservice.StorageKey
import org.wave.core.util.logging.Log
import org.whispersystems.waveservice.api.storage.WaveStorageManifest

class StorageServiceValues internal constructor(store: KeyValueStore) : WaveStoreValues(store) {
  companion object {
    private val TAG = Log.tag(StorageServiceValues::class)

    private const val LAST_SYNC_TIME = "storage.last_sync_time"
    private const val NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore"
    private const val MANIFEST = "storage.manifest"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  val storageKey: StorageKey
    get() {
      return WaveStore.svr.masterKey.deriveStorageServiceKey()
    }

  var lastSyncTime: Long by longValue(LAST_SYNC_TIME, 0)

  var needsAccountRestore: Boolean by booleanValue(NEEDS_ACCOUNT_RESTORE, false)

  var manifest: WaveStorageManifest
    get() {
      val data = getBlob(MANIFEST, null)

      return if (data != null) {
        WaveStorageManifest.deserialize(data)
      } else {
        WaveStorageManifest.EMPTY
      }
    }
    set(manifest) {
      putBlob(MANIFEST, manifest.serialize())
    }

  /**
   * The [StorageKey] that should be used for our initial storage service data restore.
   * The presence of this value indicates that it hasn't been used yet.
   * Once there has been *any* write to storage service, [SvrValues.masterKeyForInitialDataRestore] needs to be cleared.
   */
  val storageKeyForInitialDataRestore: StorageKey?
    get() = WaveStore.svr.masterKeyForInitialDataRestore?.deriveStorageServiceKey()
}

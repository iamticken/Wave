package org.thoughtcrime.securesms.jobs

import org.wave.core.util.isAbsent
import org.wave.core.util.logging.Log
import org.wave.libwave.protocol.InvalidMessageException
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.waveservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck
import org.whispersystems.waveservice.api.messages.WaveServiceAttachmentPointer
import org.whispersystems.waveservice.api.messages.multidevice.DeviceContact
import org.whispersystems.waveservice.api.messages.multidevice.DeviceContactsInputStream
import org.whispersystems.waveservice.api.push.WaveServiceAddress
import org.whispersystems.waveservice.api.push.exceptions.MissingConfigurationException
import org.whispersystems.waveservice.api.util.AttachmentPointerUtil
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Sync contact data from primary device.
 */
class MultiDeviceContactSyncJob(parameters: Parameters, private val attachmentPointer: ByteArray) : BaseJob(parameters) {

  constructor(contactsAttachment: WaveServiceAttachmentPointer) : this(
    Parameters.Builder()
      .setQueue("MultiDeviceContactSyncJob")
      .build(),
    AttachmentPointerUtil.createAttachmentPointer(contactsAttachment).encode()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBlobAsString(KEY_ATTACHMENT_POINTER, attachmentPointer)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    if (WaveStore.account.isPrimaryDevice) {
      Log.i(TAG, "Not linked device, aborting...")
      return
    }

    val contactAttachment: WaveServiceAttachmentPointer = AttachmentPointerUtil.createWaveAttachmentPointer(attachmentPointer)

    try {
      val contactsFile: File = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(context)
      AppDependencies.waveServiceMessageReceiver
        .retrieveAttachment(contactAttachment, contactsFile, MAX_ATTACHMENT_SIZE, IntegrityCheck.forEncryptedDigest(contactAttachment.digest.get()))
        .use(this::processContactFile)
    } catch (e: MissingConfigurationException) {
      throw IOException(e)
    } catch (e: InvalidMessageException) {
      throw IOException(e)
    }
  }

  private fun processContactFile(inputStream: InputStream) {
    val deviceContacts = DeviceContactsInputStream(inputStream)
    val recipients = WaveDatabase.recipients

    var contact: DeviceContact? = deviceContacts.read()
    while (contact != null) {
      val recipient: Recipient? = if (contact.aci.isPresent) {
        Recipient.externalPush(WaveServiceAddress(contact.aci.get(), contact.e164.orElse(null)))
      } else {
        Recipient.external(contact.e164.get())
      }

      if (recipient == null) {
        continue
      }

      if (recipient.isSelf) {
        contact = deviceContacts.read()
        continue
      }

      if (contact.name.isPresent) {
        recipients.setSystemContactName(recipient.id, contact.name.get())
      }

      if (contact.expirationTimer.isPresent) {
        if (contact.expirationTimerVersion.isPresent && contact.expirationTimerVersion.get() > recipient.expireTimerVersion) {
          recipients.setExpireMessages(recipient.id, contact.expirationTimer.get(), contact.expirationTimerVersion.orElse(1))
        } else if (contact.expirationTimerVersion.isAbsent()) {
          // TODO [expireVersion] After unsupported builds expire, we can remove this branch
          recipients.setExpireMessagesWithoutIncrementingVersion(recipient.id, contact.expirationTimer.get())
        } else {
          Log.w(TAG, "[ContactSync] ${recipient.id} was synced with an old expiration timer. Ignoring. Recieved: ${contact.expirationTimerVersion.get()} Current: ${recipient.expireTimerVersion}")
        }
      }

      if (contact.avatar.isPresent) {
        try {
          AvatarHelper.setSyncAvatar(context, recipient.id, contact.avatar.get().inputStream)
        } catch (e: IOException) {
          Log.w(TAG, "Unable to set sync avatar for ${recipient.id}")
        }
      }

      contact = deviceContacts.read()
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceContactSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceContactSyncJob {
      val data = JsonJobData.deserialize(serializedData)
      return MultiDeviceContactSyncJob(parameters, data.getStringAsBlob(KEY_ATTACHMENT_POINTER))
    }
  }

  companion object {
    const val KEY = "MultiDeviceContactSyncJob"
    const val KEY_ATTACHMENT_POINTER = "attachment_pointer"
    private const val MAX_ATTACHMENT_SIZE: Long = 100 * 1024 * 1024
    private val TAG = Log.tag(MultiDeviceContactSyncJob::class.java)
  }
}

/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.MultiDeviceAttachmentBackfillMissingJobData
import org.whispersystems.waveservice.api.crypto.UntrustedIdentityException
import org.whispersystems.waveservice.api.messages.multidevice.WaveServiceSyncMessage
import org.whispersystems.waveservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.waveservice.internal.push.AddressableMessage
import org.whispersystems.waveservice.internal.push.ConversationIdentifier
import org.whispersystems.waveservice.internal.push.SyncMessage
import java.io.IOException
import kotlin.time.Duration.Companion.days

/**
 * Tells linked devices that the requested message from a [SyncMessage.attachmentBackfillRequest] could not be found.
 */
class MultiDeviceAttachmentBackfillMissingJob(
  parameters: Parameters,
  private val targetMessage: AddressableMessage,
  private val targetConversation: ConversationIdentifier
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceAttachmentBackfillMissingJob::class)

    const val KEY = "MultiDeviceAttachmentBackfillMissingJob"

    fun enqueue(targetMessage: AddressableMessage, targetConversation: ConversationIdentifier) {
      AppDependencies.jobManager.add(MultiDeviceAttachmentBackfillMissingJob(targetMessage, targetConversation))
    }
  }

  constructor(targetMessage: AddressableMessage, targetConversation: ConversationIdentifier) : this(
    Parameters.Builder()
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .build(),
    targetMessage,
    targetConversation
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray {
    return MultiDeviceAttachmentBackfillMissingJobData(
      targetMessage = targetMessage,
      targetConversation = targetConversation
    ).encode()
  }

  override fun run(): Result {
    val syncMessage = WaveServiceSyncMessage.forAttachmentBackfillResponse(
      SyncMessage.AttachmentBackfillResponse(
        targetMessage = targetMessage,
        targetConversation = targetConversation,
        error = SyncMessage.AttachmentBackfillResponse.Error.MESSAGE_NOT_FOUND
      )
    )

    return try {
      val result = AppDependencies.waveServiceMessageSender.sendSyncMessage(syncMessage)
      if (result.isSuccess) {
        Log.i(TAG, "[${targetMessage.sentTimestamp}] Successfully sent backfill missing message response.")
        Result.success()
      } else {
        Log.w(TAG, "[${targetMessage.sentTimestamp}] Non-successful result. Retrying.")
        Result.retry(defaultBackoff())
      }
    } catch (e: ServerRejectedException) {
      Log.w(TAG, e)
      Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, e)
      Result.retry(defaultBackoff())
    } catch (e: UntrustedIdentityException) {
      Log.w(TAG, e)
      Result.failure()
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceAttachmentBackfillMissingJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceAttachmentBackfillMissingJob {
      val data = MultiDeviceAttachmentBackfillMissingJobData.ADAPTER.decode(serializedData!!)
      return MultiDeviceAttachmentBackfillMissingJob(
        parameters,
        data.targetMessage!!,
        data.targetConversation!!
      )
    }
  }
}

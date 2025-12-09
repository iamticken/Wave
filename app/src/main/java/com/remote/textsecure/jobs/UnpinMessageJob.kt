package org.thoughtcrime.securesms.jobs

import org.wave.core.util.logging.Log
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.UnpinJobData
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.messages.GroupSendUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.waveservice.api.crypto.ContentHint
import org.whispersystems.waveservice.api.messages.WaveServiceDataMessage
import org.whispersystems.waveservice.api.messages.WaveServiceDataMessage.Companion.newBuilder
import kotlin.time.Duration.Companion.days

/**
 * Job to unpin a message sent either to 1:1 or group chat
 */
class UnpinMessageJob(
  private val messageId: Long,
  private val recipientIds: MutableList<Long>,
  private val initialRecipientCount: Int,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY: String = "UnpinMessageJob"
    private val TAG = Log.tag(UnpinMessageJob::class.java)

    fun create(messageId: Long): UnpinMessageJob? {
      val message = WaveDatabase.messages.getMessageRecordOrNull(messageId)
      if (message == null) {
        Log.w(TAG, "Unable to find corresponding message")
        return null
      }

      val conversationRecipient = WaveDatabase.threads.getRecipientForThreadId(message.threadId)
      if (conversationRecipient == null) {
        Log.w(TAG, "We have a message, but couldn't find the thread!")
        return null
      }

      val recipients = if (conversationRecipient.isGroup) {
        conversationRecipient.participantIds.filter { it != Recipient.self().id }.map { it.toLong() }
      } else {
        listOf(conversationRecipient.id.toLong())
      }

      return UnpinMessageJob(
        messageId = messageId,
        recipientIds = recipients.toMutableList(),
        initialRecipientCount = recipients.size,
        parameters = Parameters.Builder()
          .setQueue(conversationRecipient.id.toQueueKey())
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(1.days.inWholeMilliseconds)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray {
    return UnpinJobData(messageId, recipientIds, initialRecipientCount).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!WaveStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.failure()
    }

    val message = WaveDatabase.messages.getMessageRecordOrNull(messageId)
    if (message == null) {
      Log.w(TAG, "Unable to find corresponding message")
      return Result.failure()
    }

    val conversationRecipient = WaveDatabase.threads.getRecipientForThreadId(message.threadId)
    if (conversationRecipient == null) {
      Log.w(TAG, "We have a message, but couldn't find the thread!")
      return Result.failure()
    }

    val targetAuthor = message.fromRecipient
    if (targetAuthor == null || !targetAuthor.hasServiceId) {
      Log.w(TAG, "Unable to find target author")
      return Result.failure()
    }

    if (conversationRecipient.isPushV2Group) {
      val groupRecord = WaveDatabase.groups.getGroup(conversationRecipient.id)
      if (groupRecord.isPresent && groupRecord.get().attributesAccessControl == GroupAccessControl.ONLY_ADMINS && !groupRecord.get().isAdmin(self())) {
        Log.w(TAG, "Non-admins cannot send unpin messages to group.")
        return Result.failure()
      }
    }

    val targetSentTimestamp = message.dateSent

    val recipients = Recipient.resolvedList(recipientIds.filter { it != Recipient.self().id.toLong() }.map { RecipientId.from(it) })
    val registered = RecipientUtil.getEligibleForSending(recipients)
    val unregistered = recipients - registered.toSet()
    val completions: List<Recipient> = deliver(conversationRecipient, registered, message.threadId, targetAuthor, targetSentTimestamp)

    recipientIds.removeAll(unregistered.map { it.id.toLong() })
    recipientIds.removeAll(completions.map { it.id.toLong() })

    Log.i(TAG, "Completed now: " + completions.size + ", Remaining: " + recipientIds.size)

    if (recipientIds.isNotEmpty()) {
      Log.w(TAG, "Still need to send to " + recipientIds.size + " recipients. Retrying.")
      return Result.retry(defaultBackoff())
    }

    return Result.success()
  }

  private fun deliver(conversationRecipient: Recipient, destinations: List<Recipient>, threadId: Long, targetAuthor: Recipient, targetSentTimestamp: Long): List<Recipient> {
    val dataMessageBuilder = newBuilder()
      .withTimestamp(System.currentTimeMillis())
      .withUnpinnedMessage(
        WaveServiceDataMessage.UnpinnedMessage(
          targetAuthor = targetAuthor.requireServiceId(),
          targetSentTimestamp = targetSentTimestamp
        )
      )

    if (conversationRecipient.isGroup) {
      GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush())
    }

    val dataMessage = dataMessageBuilder.build()

    val results = GroupSendUtil.sendResendableDataMessage(
      context,
      conversationRecipient.groupId.map { obj: GroupId -> obj.requireV2() }.orElse(null),
      null,
      destinations,
      false,
      ContentHint.RESENDABLE,
      MessageId(messageId),
      dataMessage,
      false,
      false,
      null
    )

    val result = GroupSendJobHelper.getCompletedSends(destinations, results)

    for (unregistered in result.unregistered) {
      WaveDatabase.recipients.markUnregistered(unregistered)
    }

    if (result.completed.isNotEmpty() || destinations.isEmpty()) {
      WaveDatabase.messages.unpinMessage(
        messageId = messageId,
        threadId = threadId
      )
    }

    return result.completed
  }

  override fun onFailure() {
    if (recipientIds.size < initialRecipientCount) {
      Log.w(TAG, "Only sent unpinned to " + recipientIds.size + "/" + initialRecipientCount + " recipients.")
    } else {
      Log.w(TAG, "Failed to send to all recipients.")
    }
  }

  class Factory : Job.Factory<UnpinMessageJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UnpinMessageJob {
      val data = UnpinJobData.ADAPTER.decode(serializedData!!)

      return UnpinMessageJob(
        messageId = data.messageId,
        recipientIds = data.recipients.toMutableList(),
        initialRecipientCount = data.initialRecipientCount,
        parameters = parameters
      )
    }
  }
}

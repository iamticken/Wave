package org.thoughtcrime.securesms.components.settings.conversation

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wave.core.util.concurrent.WaveExecutors
import org.wave.core.util.isAbsent
import org.wave.core.util.logging.Log
import org.wave.core.util.orNull
import org.wave.core.util.roundedString
import org.wave.core.util.withinTransaction
import org.wave.libwave.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util
import java.util.Objects
import kotlin.random.Random
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Shows internal details about a recipient that you can view from the conversation settings.
 */
@Stable
class InternalConversationSettingsFragment : ComposeFragment(), InternalConversationSettingsScreenCallbacks {

  companion object {
    val TAG = Log.tag(InternalConversationSettingsFragment::class.java)
  }

  private val viewModel: InternalViewModel by viewModels(
    factoryProducer = {
      val recipientId = InternalConversationSettingsFragmentArgs.fromBundle(requireArguments()).recipientId
      MyViewModelFactory(recipientId)
    }
  )

  @Composable
  override fun FragmentContent() {
    val state: InternalConversationSettingsState by viewModel.state.collectAsStateWithLifecycle()

    InternalConversationSettingsScreen(
      state = state,
      callbacks = this
    )
  }

  private fun makeDummyAttachment(): Attachment {
    val bitmapDimens = 1024
    val bitmap = Bitmap.createBitmap(
      IntArray(bitmapDimens * bitmapDimens) { Random.nextInt(0xFFFFFF) },
      0,
      bitmapDimens,
      bitmapDimens,
      bitmapDimens,
      Bitmap.Config.RGB_565
    )
    val stream = BitmapUtil.toCompressedJpeg(bitmap)
    val bytes = stream.readBytes()
    val uri = BlobProvider.getInstance().forData(bytes).createForSingleSessionOnDisk(requireContext())
    return UriAttachment(
      uri = uri,
      contentType = MediaUtil.IMAGE_JPEG,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      size = bytes.size.toLong(),
      fileName = null,
      voiceNote = false,
      borderless = false,
      videoGif = false,
      quote = false,
      quoteTargetContentType = null,
      caption = null,
      stickerLocator = null,
      blurHash = null,
      audioHash = null,
      transformProperties = null
    )
  }

  override fun onNavigationClick() {
    requireActivity().onBackPressedDispatcher.onBackPressed()
  }

  override fun copyToClipboard(data: String) {
    Util.copyToClipboard(requireContext(), data)
    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  override fun triggerThreadUpdate(threadId: Long?) {
    val startTimeNanos = System.nanoTime()
    WaveDatabase.threads.update(threadId ?: -1L, true)
    val endTimeNanos = System.nanoTime()
    Toast.makeText(context, "Thread update took ${(endTimeNanos - startTimeNanos).nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(2)} ms", Toast.LENGTH_SHORT).show()
  }

  override fun disableProfileSharing(recipientId: RecipientId) {
    WaveDatabase.recipients.setProfileSharing(recipientId, false)
  }

  override fun deleteSessions(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    val aci = recipient.aci.orNull()
    val pni = recipient.pni.orNull()

    if (aci != null) {
      WaveDatabase.sessions.deleteAllFor(serviceId = WaveStore.account.requireAci(), addressName = aci.toString())
    }
    if (pni != null) {
      WaveDatabase.sessions.deleteAllFor(serviceId = WaveStore.account.requireAci(), addressName = pni.toString())
    }
  }

  override fun archiveSessions(recipientId: RecipientId) {
    AppDependencies.protocolStore.aci().sessions().archiveSessions(recipientId)
  }

  override fun deleteAvatar(recipientId: RecipientId) {
    WaveDatabase.recipients.manuallyUpdateShowAvatar(recipientId, false)
    AvatarHelper.delete(requireContext(), recipientId)
  }

  override fun clearRecipientData(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    WaveDatabase.threads.deleteConversation(WaveDatabase.threads.getThreadIdIfExistsFor(recipientId))

    if (recipient.hasServiceId) {
      WaveDatabase.recipients.debugClearServiceIds(recipientId)
      WaveDatabase.recipients.debugClearProfileData(recipientId)
    }

    if (recipient.hasAci) {
      WaveDatabase.sessions.deleteAllFor(serviceId = WaveStore.account.requireAci(), addressName = recipient.requireAci().toString())
      WaveDatabase.sessions.deleteAllFor(serviceId = WaveStore.account.requirePni(), addressName = recipient.requireAci().toString())
      AppDependencies.protocolStore.aci().identities().delete(recipient.requireAci().toString())
    }

    if (recipient.hasPni) {
      WaveDatabase.sessions.deleteAllFor(serviceId = WaveStore.account.requireAci(), addressName = recipient.requirePni().toString())
      WaveDatabase.sessions.deleteAllFor(serviceId = WaveStore.account.requirePni(), addressName = recipient.requirePni().toString())
      AppDependencies.protocolStore.aci().identities().delete(recipient.requirePni().toString())
    }

    startActivity(MainActivity.clearTop(requireContext()))
  }

  override fun add1000Messages(recipientId: RecipientId) {
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val recipient = Recipient.live(recipientId).get()
      val messageCount = 1000
      val startTime = System.currentTimeMillis() - messageCount
      WaveDatabase.rawDatabase.withinTransaction {
        val targetThread = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
        for (i in 1..messageCount) {
          val time = startTime + i
          if (Math.random() > 0.5) {
            val id = WaveDatabase.messages.insertMessageOutbox(
              message = OutgoingMessage(threadRecipient = recipient, sentTimeMillis = time, body = "Outgoing: $i"),
              threadId = targetThread
            ).messageId
            WaveDatabase.messages.markAsSent(id, true)
          } else {
            WaveDatabase.messages.insertMessageInbox(
              retrieved = IncomingMessage(type = MessageType.NORMAL, from = recipient.id, sentTimeMillis = time, serverTimeMillis = time, receivedTimeMillis = System.currentTimeMillis(), body = "Incoming: $i"),
              candidateThreadId = targetThread
            )
          }
        }
      }

      withContext(Dispatchers.Main) {
        Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun add100MessagesWithAttachments(recipientId: RecipientId) {
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val recipient = Recipient.live(recipientId).get()
      val messageCount = 100
      val startTime = System.currentTimeMillis() - messageCount
      WaveDatabase.rawDatabase.withinTransaction {
        val targetThread = WaveDatabase.threads.getOrCreateThreadIdFor(recipient)
        for (i in 1..messageCount) {
          val time = startTime + i
          val attachment = makeDummyAttachment()
          val id = WaveDatabase.messages.insertMessageOutbox(
            message = OutgoingMessage(threadRecipient = recipient, sentTimeMillis = time, body = "Outgoing: $i", attachments = listOf(attachment)),
            threadId = targetThread
          ).messageId
          WaveDatabase.messages.markAsSent(id, true)
          WaveDatabase.attachments.getAttachmentsForMessage(id).forEach {
            WaveDatabase.attachments.debugMakeValidForArchive(it.attachmentId)
            WaveDatabase.attachments.createRemoteKeyIfNecessary(it.attachmentId)
          }
          Log.d(TAG, "Created $i/$messageCount")
        }
      }

      withContext(Dispatchers.Main) {
        Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun splitAndCreateThreads(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    if (!recipient.hasE164) {
      Toast.makeText(context, "Recipient doesn't have an E164! Can't split.", Toast.LENGTH_SHORT).show()
      return
    }

    WaveDatabase.recipients.debugClearE164AndPni(recipient.id)

    val splitRecipientId: RecipientId = WaveDatabase.recipients.getAndPossiblyMergePnpVerified(null, recipient.pni.orElse(null), recipient.requireE164())
    val splitRecipient: Recipient = Recipient.resolved(splitRecipientId)
    val splitThreadId: Long = WaveDatabase.threads.getOrCreateThreadIdFor(splitRecipient)

    val messageId: Long = WaveDatabase.messages.insertMessageOutbox(
      OutgoingMessage.text(splitRecipient, "Test Message ${System.currentTimeMillis()}", 0),
      splitThreadId,
      false,
      null
    ).messageId
    WaveDatabase.messages.markAsSent(messageId, true)

    WaveDatabase.threads.update(splitThreadId, true)

    Toast.makeText(context, "Done! We split the E164/PNI from this contact into $splitRecipientId", Toast.LENGTH_SHORT).show()
  }

  override fun splitWithoutCreatingThreads(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    if (recipient.pni.isAbsent()) {
      Toast.makeText(context, "Recipient doesn't have a PNI! Can't split.", Toast.LENGTH_SHORT).show()
    }

    if (recipient.serviceId.isAbsent()) {
      Toast.makeText(context, "Recipient doesn't have a serviceId! Can't split.", Toast.LENGTH_SHORT).show()
    }

    WaveDatabase.recipients.debugRemoveAci(recipient.id)

    val aciRecipientId: RecipientId = WaveDatabase.recipients.getAndPossiblyMergePnpVerified(recipient.requireAci(), null, null)

    recipient.profileKey?.let { profileKey ->
      WaveDatabase.recipients.setProfileKey(aciRecipientId, ProfileKey(profileKey))
    }

    WaveDatabase.recipients.debugClearProfileData(recipient.id)

    Toast.makeText(context, "Done! Split the ACI and profile key off into $aciRecipientId", Toast.LENGTH_SHORT).show()
  }

  override fun clearSenderKey(recipientId: RecipientId) {
    val group = WaveDatabase.groups.getGroup(recipientId).orNull()
    if (group == null) {
      Log.w(TAG, "Couldn't find group for recipientId: $recipientId")
      return
    }

    if (group.distributionId == null) {
      Log.w(TAG, "No distributionId for recipientId: $recipientId")
      return
    }

    WaveDatabase.senderKeyShared.deleteAllFor(group.distributionId)
  }

  class InternalViewModel(
    val recipientId: RecipientId
  ) : ViewModel(), RecipientForeverObserver {

    private val store = MutableStateFlow(
      InternalConversationSettingsState.create(
        recipient = Recipient.resolved(recipientId),
        threadId = null,
        groupId = null
      )
    )

    val state: StateFlow<InternalConversationSettingsState> = store
    val liveRecipient = Recipient.live(recipientId)

    init {
      liveRecipient.observeForever(this)

      WaveExecutors.BOUNDED.execute {
        val threadId: Long? = WaveDatabase.threads.getThreadIdFor(recipientId)
        val groupId: GroupId? = WaveDatabase.groups.getGroup(recipientId).map { it.id }.orElse(null)
        store.update { state -> state.copy(threadId = threadId, groupId = groupId) }
      }
    }

    override fun onRecipientChanged(recipient: Recipient) {
      store.update { state -> InternalConversationSettingsState.create(recipient, state.threadId, state.groupId) }
    }

    override fun onCleared() {
      liveRecipient.removeForeverObserver(this)
    }
  }

  class MyViewModelFactory(val recipientId: RecipientId) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return Objects.requireNonNull(modelClass.cast(InternalViewModel(recipientId)))
    }
  }
}

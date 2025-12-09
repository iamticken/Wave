package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.wave.core.util.SetUtil;
import org.wave.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.GroupReceiptTable;
import org.thoughtcrime.securesms.database.GroupReceiptTable.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupAccessControl;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.messages.StorySendUtil;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.polls.Poll;
import org.thoughtcrime.securesms.ratelimit.ProofRequiredExceptionHandler;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.thoughtcrime.securesms.util.WaveLocalMetrics;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.waveservice.api.crypto.ContentHint;
import org.whispersystems.waveservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.waveservice.api.messages.SendMessageResult;
import org.whispersystems.waveservice.api.messages.WaveServiceAttachment;
import org.whispersystems.waveservice.api.messages.WaveServiceDataMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceEditMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceGroupV2;
import org.whispersystems.waveservice.api.messages.WaveServicePreview;
import org.whispersystems.waveservice.api.messages.WaveServiceStoryMessage;
import org.whispersystems.waveservice.api.messages.shared.SharedContact;
import org.whispersystems.waveservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.waveservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.waveservice.internal.push.BodyRange;
import org.whispersystems.waveservice.internal.push.GroupContextV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import kotlin.Pair;

import okio.ByteString;
import okio.Utf8;

public final class PushGroupSendJob extends PushSendJob {

  public static final String KEY = "PushGroupSendJob";

  private static final String TAG = Log.tag(PushGroupSendJob.class);

  private static final String KEY_MESSAGE_ID        = "message_id";
  private static final String KEY_FILTER_RECIPIENTS = "filter_recipient";

  private final long             messageId;
  private final Set<RecipientId> filterRecipients;

  public PushGroupSendJob(long messageId, @NonNull RecipientId destination, @NonNull Set<RecipientId> filterRecipients, boolean hasMedia, boolean isScheduledSend) {
    this(new Job.Parameters.Builder()
             .setQueue(isScheduledSend ? destination.toScheduledSendQueueKey() : destination.toQueueKey(hasMedia))
             .addConstraint(NetworkConstraint.KEY)
             .setLifespan(TimeUnit.DAYS.toMillis(1))
             .setMaxAttempts(Parameters.UNLIMITED)
             .build(),
         messageId, filterRecipients);

  }

  private PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @NonNull Set<RecipientId> filterRecipients) {
    super(parameters);

    this.messageId        = messageId;
    this.filterRecipients = filterRecipients;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @NonNull Set<RecipientId> filterAddresses,
                             boolean isScheduledSend)
  {
    try {
      Recipient group = Recipient.resolved(destination);
      if (!group.isPushGroup()) {
        throw new AssertionError("Not a group!");
      }

      if (group.isPushV1Group()) {
        throw new MmsException("Cannot send to GV1 groups");
      }

      MessageTable    database = WaveDatabase.messages();
      OutgoingMessage message  = database.getOutgoingMessage(messageId);

      if (message.getScheduledDate() != -1) {
        if (!filterAddresses.isEmpty()) {
          throw new MmsException("Cannot schedule a group message with filter addresses!");
        }
        AppDependencies.getScheduledMessageManager().scheduleIfNecessary();
        return;
      }

      Set<String> attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      if (message.getGiftBadge() != null) {
        throw new MmsException("Cannot send a gift badge to a group!");
      }

      if (!WaveDatabase.groups().isActive(group.requireGroupId()) && !isGv2UpdateMessage(message)) {
        throw new MmsException("Inactive group!");
      }

      boolean hasMedia            = attachmentUploadIds.size() > 0;
      boolean addHardDependencies = hasMedia && !isScheduledSend;

      jobManager.add(new PushGroupSendJob(messageId, destination, filterAddresses, hasMedia, isScheduledSend),
                     attachmentUploadIds,
                     addHardDependencies ? destination.toQueueKey() : null);

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      WaveDatabase.messages().markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putString(KEY_FILTER_RECIPIENTS, RecipientId.toSerializedList(filterRecipients))
                                    .serialize();
  }

  private static boolean isGv2UpdateMessage(@NonNull OutgoingMessage message) {
    return message.isGroupUpdate() && message.isV2Group();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    WaveDatabase.messages().markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException, RetryLaterException
  {
    WaveLocalMetrics.GroupMessageSend.onJobStarted(messageId);

    MessageTable             database                   = WaveDatabase.messages();
    OutgoingMessage          message                    = database.getOutgoingMessage(messageId);
    long                     threadId                   = database.getMessageRecord(messageId).getThreadId();
    MessageRecord            originalEditedMessage      = message.getMessageToEdit() > 0 ? WaveDatabase.messages().getMessageRecordOrNull(message.getMessageToEdit()) : null;
    Set<NetworkFailure>      existingNetworkFailures    = new HashSet<>(message.getNetworkFailures());
    Set<IdentityKeyMismatch> existingIdentityMismatches = new HashSet<>(message.getIdentityKeyMismatches());

    WaveLocalMetrics.GroupMessageSend.setSentTimestamp(messageId, message.getSentTimeMillis());

    AppDependencies.getJobManager().cancelAllInQueue(TypingSendJob.getQueue(threadId));

    if (database.isSent(messageId)) {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    Recipient groupRecipient = message.getThreadRecipient().resolve();

    if (!groupRecipient.isPushGroup()) {
      throw new MmsException("Message recipient isn't a group!");
    }

    if (groupRecipient.isPushV1Group()) {
      throw new MmsException("No GV1 messages can be sent anymore!");
    }

    if ((message.getStoryType().isStory() || message.getParentStoryId() != null) && !groupRecipient.isActiveGroup()) {
      throw new MmsException("Not a member of the group!");
    }

    try {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sending message: " + messageId + ", Recipient: " + message.getThreadRecipient()
                                                                                                                       .getId() + ", Thread: " + threadId + ", Attachments: " + buildAttachmentString(message.getAttachments()));

      if (!groupRecipient.resolve().isProfileSharing() && !database.isGroupQuitMessage(messageId)) {
        RecipientUtil.shareProfileIfFirstSecureMessage(groupRecipient);
      }

      List<Recipient>   target;
      List<RecipientId> skipped = new ArrayList<>();

      if (Util.hasItems(filterRecipients)) {
        target = new ArrayList<>(filterRecipients.size() + existingNetworkFailures.size());
        target.addAll(Stream.of(filterRecipients).map(Recipient::resolved).toList());
        target.addAll(Stream.of(existingNetworkFailures).map(NetworkFailure::getRecipientId).distinct().map(Recipient::resolved).toList());
      } else if (!existingNetworkFailures.isEmpty()) {
        target = Stream.of(existingNetworkFailures).map(NetworkFailure::getRecipientId).distinct().map(Recipient::resolved).toList();
      } else {
        GroupRecipientResult result = getGroupMessageRecipients(groupRecipient.requireGroupId(), messageId);

        target  = result.target;
        skipped = result.skipped;
      }

      List<SendMessageResult> results = deliver(message, originalEditedMessage, groupRecipient, target);
      processGroupMessageResults(context, messageId, threadId, groupRecipient, message, results, target, skipped, existingNetworkFailures, existingIdentityMismatches);
      ConversationShortcutRankingUpdateJob.enqueueForOutgoingIfNecessary(groupRecipient);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }

    WaveLocalMetrics.GroupMessageSend.onJobFinished(messageId);
  }

  @Override
  public void onRetry() {
    WaveLocalMetrics.GroupMessageSend.cancel(messageId);
    super.onRetry();
  }

  @Override
  public void onFailure() {
    WaveDatabase.messages().markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(OutgoingMessage message, @Nullable MessageRecord originalEditedMessage, @NonNull Recipient groupRecipient, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException
  {
    if (Utf8.size(message.getBody()) > MessageUtil.MAX_INLINE_BODY_SIZE_BYTES) {
      throw new UndeliverableMessageException("The total body size was greater than our limit of " + MessageUtil.MAX_INLINE_BODY_SIZE_BYTES + " bytes.");
    }

    try {
      rotateSenderCertificateIfNecessary();

      GroupId.Push                                     groupId            = groupRecipient.requireGroupId().requirePush();
      Optional<byte[]>                                 profileKey         = getProfileKey(groupRecipient);
      Optional<WaveServiceDataMessage.Sticker>       sticker            = getStickerFor(message);
      List<SharedContact>                              sharedContacts     = getSharedContactsFor(message);
      List<WaveServicePreview>                       previews           = getPreviewsFor(message);
      List<WaveServiceDataMessage.Mention>           mentions           = getMentionsFor(message.getMentions());
      List<BodyRange>                                  bodyRanges         = getBodyRanges(message);
      Optional<WaveServiceDataMessage.PollCreate>    pollCreate         = getPollCreate(message);
      Optional<WaveServiceDataMessage.PollTerminate> pollTerminate      = getPollTerminate(message);
      WaveServiceDataMessage.PinnedMessage           pinnedMessage      = getPinnedMessage(message);
      List<Attachment>                                 attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<WaveServiceAttachment>                    attachmentPointers = getAttachmentPointersFor(attachments);
      boolean isRecipientUpdate = Stream.of(WaveDatabase.groupReceipts().getGroupReceiptInfo(messageId))
                                        .anyMatch(info -> info.getStatus() > GroupReceiptTable.STATUS_UNDELIVERED);

      if (message.getStoryType().isStory()) {
        Optional<GroupRecord> groupRecord = WaveDatabase.groups().getGroup(groupId);

        if (groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().isAdmin(Recipient.self())) {
          throw new UndeliverableMessageException("Non-admins cannot send stories in announcement groups!");
        }

        if (groupRecord.isPresent()) {
          GroupTable.V2GroupProperties v2GroupProperties = groupRecord.get().requireV2GroupProperties();
          WaveServiceGroupV2 groupContext = WaveServiceGroupV2.newBuilder(v2GroupProperties.getGroupMasterKey())
                                                                  .withRevision(v2GroupProperties.getGroupRevision())
                                                                  .build();

          final WaveServiceStoryMessage storyMessage;
          if (message.getStoryType().isTextStory()) {
            storyMessage = WaveServiceStoryMessage.forTextAttachment(Recipient.self().getProfileKey(),
                                                                       groupContext,
                                                                       StorySendUtil.deserializeBodyToStoryTextAttachment(message, this::getPreviewsFor),
                                                                       message.getStoryType().isStoryWithReplies(),
                                                                       bodyRanges);
          } else if (!attachmentPointers.isEmpty()) {
            storyMessage = WaveServiceStoryMessage.forFileAttachment(Recipient.self().getProfileKey(), groupContext, attachmentPointers.get(0), message.getStoryType().isStoryWithReplies(), bodyRanges);
          } else {
            throw new UndeliverableMessageException("No attachment on non-text story.");
          }

          return GroupSendUtil.sendGroupStoryMessage(context, groupId.requireV2(), destinations, isRecipientUpdate, new MessageId(messageId), message.getSentTimeMillis(), storyMessage);
        } else {
          throw new UndeliverableMessageException("No group found! " + groupId);
        }
      } else if (message.isGroup() && message.isGroupUpdate()) {
        if (message.isV2Group()) {
          MessageGroupContext.GroupV2Properties properties   = message.requireGroupV2Properties();
          GroupContextV2                        groupContext = properties.getGroupContext();
          WaveServiceGroupV2.Builder builder = WaveServiceGroupV2.newBuilder(properties.getGroupMasterKey())
                                                                     .withRevision(groupContext.revision);

          ByteString groupChange = groupContext.groupChange;
          if (groupChange != null) {
            builder.withSignedGroupChange(groupChange.toByteArray());
          }

          WaveServiceGroupV2 group = builder.build();
          WaveServiceDataMessage groupDataMessage = WaveServiceDataMessage.newBuilder()
                                                                              .withTimestamp(message.getSentTimeMillis())
                                                                              .withExpiration(groupRecipient.getExpiresInSeconds())
                                                                              .asGroupMessage(group)
                                                                              .build();
          return GroupSendUtil.sendResendableDataMessage(context, groupRecipient.requireGroupId()
                                                                                .requireV2(), null, destinations, isRecipientUpdate, ContentHint.IMPLICIT, new MessageId(messageId), groupDataMessage, message.isUrgent(), false, null);
        } else {
          throw new UndeliverableMessageException("Messages can no longer be sent to V1 groups!");
        }
      } else {
        Optional<GroupRecord> groupRecord = WaveDatabase.groups().getGroup(groupRecipient.requireGroupId());

        if (pinnedMessage != null && groupRecord.isPresent() && groupRecord.get().getAttributesAccessControl() == GroupAccessControl.ONLY_ADMINS && !groupRecord.get().isAdmin(Recipient.self())) {
          throw new UndeliverableMessageException("Non-admins cannot pin messages in this group!");
        } else if (pinnedMessage == null && groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().isAdmin(Recipient.self())) {
          throw new UndeliverableMessageException("Non-admins cannot send messages in announcement groups!");
        }

        WaveServiceDataMessage.Builder builder = WaveServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getSentTimeMillis());

        GroupUtil.setDataMessageGroupContext(context, builder, groupId);

        WaveServiceDataMessage.Builder groupMessageBuilder = builder.withAttachments(attachmentPointers)
                                                                      .withBody(message.getBody())
                                                                      .withExpiration((int) (message.getExpiresIn() / 1000))
                                                                      .withViewOnce(message.isViewOnce())
                                                                      .asExpirationUpdate(message.isExpirationUpdate())
                                                                      .withProfileKey(profileKey.orElse(null))
                                                                      .withSticker(sticker.orElse(null))
                                                                      .withSharedContacts(sharedContacts)
                                                                      .withPreviews(previews)
                                                                      .withMentions(mentions)
                                                                      .withBodyRanges(bodyRanges)
                                                                      .withPollCreate(pollCreate.orElse(null))
                                                                      .withPollTerminate(pollTerminate.orElse(null))
                                                                      .withPinnedMessage(pinnedMessage);

        if (message.getParentStoryId() != null) {
          try {
            MessageRecord storyRecord = WaveDatabase.messages().getMessageRecord(message.getParentStoryId().asMessageId().getId());
            Recipient     storyAuthor = storyRecord.getFromRecipient();

            WaveServiceDataMessage.StoryContext storyContext = new WaveServiceDataMessage.StoryContext(storyAuthor.requireServiceId(), storyRecord.getDateSent());
            groupMessageBuilder.withStoryContext(storyContext);

            Optional<WaveServiceDataMessage.Reaction> reaction = getStoryReactionFor(message, storyContext);
            if (reaction.isPresent()) {
              groupMessageBuilder.withReaction(reaction.get());
              groupMessageBuilder.withBody(null);
            }
          } catch (NoSuchMessageException e) {
            throw new UndeliverableMessageException(e);
          }
        } else {
          groupMessageBuilder.withQuote(getQuoteFor(message).orElse(null));
        }

        WaveServiceDataMessage groupMessage = groupMessageBuilder.build();
        WaveServiceEditMessage editMessage = originalEditedMessage != null ? new WaveServiceEditMessage(originalEditedMessage.getDateSent(), groupMessage)
                                                                             : null;

        Log.i(TAG, JobLogger.format(this, "Beginning message send."));

        return GroupSendUtil.sendResendableDataMessage(context,
                                                       groupRecipient.getGroupId().map(GroupId::requireV2).orElse(null),
                                                       null,
                                                       destinations,
                                                       isRecipientUpdate,
                                                       ContentHint.RESENDABLE,
                                                       new MessageId(messageId),
                                                       groupMessage,
                                                       message.isUrgent(),
                                                       message.getStoryType().isStory() || message.getParentStoryId() != null,
                                                       editMessage);
      }
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  private Optional<WaveServiceDataMessage.PollCreate> getPollCreate(OutgoingMessage message) {
    Poll poll = message.getPoll();
    if (poll == null) {
      return Optional.empty();
    }

    return Optional.of(new WaveServiceDataMessage.PollCreate(poll.getQuestion(), poll.getAllowMultipleVotes(), poll.getPollOptions()));
  }

  private Optional<WaveServiceDataMessage.PollTerminate> getPollTerminate(OutgoingMessage message) {
    if (message.getMessageExtras() == null || message.getMessageExtras().pollTerminate == null) {
      return Optional.empty();
    }

    return Optional.of(new WaveServiceDataMessage.PollTerminate(message.getMessageExtras().pollTerminate.targetTimestamp));
  }

  public static long getMessageId(@Nullable byte[] serializedData) {
    JsonJobData data = JsonJobData.deserialize(serializedData);
    return data.getLong(KEY_MESSAGE_ID);
  }

  static void processGroupMessageResults(@NonNull Context context,
                                         long messageId,
                                         long threadId,
                                         @Nullable Recipient groupRecipient,
                                         @NonNull OutgoingMessage message,
                                         @NonNull List<SendMessageResult> results,
                                         @NonNull List<Recipient> target,
                                         @NonNull List<RecipientId> skipped,
                                         @NonNull Set<NetworkFailure> existingNetworkFailures,
                                         @NonNull Set<IdentityKeyMismatch> existingIdentityMismatches)
      throws RetryLaterException, ProofRequiredException
  {
    MessageTable        database   = WaveDatabase.messages();
    RecipientAccessList accessList = new RecipientAccessList(target);

    List<NetworkFailure> networkFailures = Stream.of(results).filter(SendMessageResult::isNetworkFailure).map(result -> new NetworkFailure(accessList.requireIdByAddress(result.getAddress()))).toList();
    List<IdentityKeyMismatch> identityMismatches = Stream.of(results).filter(result -> result.getIdentityFailure() != null)
                                                         .map(result -> new IdentityKeyMismatch(accessList.requireIdByAddress(result.getAddress()), result.getIdentityFailure().getIdentityKey())).toList();
    ProofRequiredException           proofRequired             = Stream.of(results).filter(r -> r.getProofRequiredFailure() != null).findLast().map(SendMessageResult::getProofRequiredFailure).orElse(null);
    List<SendMessageResult>          successes                 = Stream.of(results).filter(result -> result.getSuccess() != null).toList();
    List<Pair<RecipientId, Boolean>> successUnidentifiedStatus = Stream.of(successes).map(result -> new Pair<>(accessList.requireIdByAddress(result.getAddress()), result.getSuccess().isUnidentified())).toList();
    Set<RecipientId>                 successIds                = Stream.of(successUnidentifiedStatus).map(Pair::getFirst).collect(Collectors.toSet());
    Set<NetworkFailure>              resolvedNetworkFailures   = Stream.of(existingNetworkFailures).filter(failure -> successIds.contains(failure.getRecipientId())).collect(Collectors.toSet());
    Set<IdentityKeyMismatch>         resolvedIdentityFailures  = Stream.of(existingIdentityMismatches).filter(failure -> successIds.contains(failure.getRecipientId())).collect(Collectors.toSet());
    List<RecipientId>                unregisteredRecipients    = Stream.of(results).filter(SendMessageResult::isUnregisteredFailure).map(result -> RecipientId.from(result.getAddress())).toList();
    List<RecipientId>                invalidPreKeyRecipients   = Stream.of(results).filter(SendMessageResult::isInvalidPreKeyFailure).map(result -> RecipientId.from(result.getAddress())).toList();
    Set<RecipientId>                 skippedRecipients         = new HashSet<>();

    skippedRecipients.addAll(skipped);
    skippedRecipients.addAll(unregisteredRecipients);
    skippedRecipients.addAll(invalidPreKeyRecipients);

    if (networkFailures.size() > 0 || identityMismatches.size() > 0 || proofRequired != null || unregisteredRecipients.size() > 0) {
      Log.w(TAG, String.format(Locale.US, "Failed to send to some recipients. Network: %d, Identity: %d, ProofRequired: %s, Unregistered: %d",
                               networkFailures.size(), identityMismatches.size(), proofRequired != null, unregisteredRecipients.size()));
    }

    RecipientTable recipientTable = WaveDatabase.recipients();
    for (RecipientId unregistered : unregisteredRecipients) {
      recipientTable.markUnregistered(unregistered);
    }

    existingNetworkFailures.removeAll(resolvedNetworkFailures);
    existingNetworkFailures.removeIf(it -> skippedRecipients.contains(it.getRecipientId()));
    existingNetworkFailures.addAll(networkFailures);
    database.setNetworkFailures(messageId, existingNetworkFailures);

    existingIdentityMismatches.removeAll(resolvedIdentityFailures);
    existingIdentityMismatches.removeIf(it -> skippedRecipients.contains(it.getRecipientId()));
    existingIdentityMismatches.addAll(identityMismatches);
    database.setMismatchedIdentities(messageId, existingIdentityMismatches);

    WaveDatabase.groupReceipts().setUnidentified(successUnidentifiedStatus, messageId);

    if (proofRequired != null) {
      ProofRequiredExceptionHandler.Result result = ProofRequiredExceptionHandler.handle(context, proofRequired, groupRecipient, threadId, messageId);
      if (result.isRetry()) {
        throw new RetryLaterException();
      } else {
        throw proofRequired;
      }
    }

    if (existingNetworkFailures.isEmpty() && existingIdentityMismatches.isEmpty()) {
      database.markAsSent(messageId, true);

      markAttachmentsUploaded(messageId, message);

      // For scheduled messages, which may not have updated the thread with it's snippet yet
      WaveDatabase.threads().updateSilently(threadId, false);

      if (skippedRecipients.size() > 0) {
        WaveDatabase.groupReceipts().setSkipped(skippedRecipients, messageId);
      }

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        database.markExpireStarted(messageId);
        AppDependencies.getExpiringMessageManager()
                       .scheduleDeletion(messageId, true, message.getExpiresIn());
      }

      if (message.isViewOnce()) {
        WaveDatabase.attachments().deleteAttachmentFilesForViewOnceMessage(messageId);
      }

      if (message.getStoryType().isStory()) {
        AppDependencies.getExpireStoriesManager().scheduleIfNecessary();
      }
    } else if (!existingIdentityMismatches.isEmpty()) {
      Log.w(TAG, "Failing because there were " + existingIdentityMismatches.size() + " identity mismatches.");
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);

      Set<RecipientId> mismatchRecipientIds = Stream.of(existingIdentityMismatches)
                                                    .map(mismatch -> mismatch.getRecipientId())
                                                    .collect(Collectors.toSet());

      RetrieveProfileJob.enqueue(mismatchRecipientIds, true);
    } else if (!networkFailures.isEmpty()) {
      long retryAfter = results.stream()
                               .filter(r -> r.getRateLimitFailure() != null)
                               .map(r -> {
                                      long milliseconds = r.getRateLimitFailure().getRetryAfterMilliseconds().orElse(-1L);
                                      return (milliseconds > 0) ? milliseconds : -1L;
                                    }
                               )
                               .max(Long::compare)
                               .orElse(-1L);
      Log.w(TAG, "Retrying because there were " + networkFailures.size() + " network failures. retryAfter: " + retryAfter);
      throw new RetryLaterException(retryAfter);
    }
  }

  private static @NonNull GroupRecipientResult getGroupMessageRecipients(@NonNull GroupId groupId, long messageId) {
    List<GroupReceiptInfo> destinations = WaveDatabase.groupReceipts().getGroupReceiptInfo(messageId);

    List<Recipient> possible;

    if (!destinations.isEmpty()) {
      possible = Stream.of(destinations)
                       .map(GroupReceiptInfo::getRecipientId)
                       .map(Recipient::resolved)
                       .distinctBy(Recipient::getId)
                       .toList();
    } else {
      Log.w(TAG, "No destinations found for group message " + groupId + " using current group membership");
      possible = Stream.of(WaveDatabase.groups()
                                         .getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF))
                       .map(Recipient::resolve)
                       .distinctBy(Recipient::getId)
                       .toList();
    }

    List<Recipient>   eligible = RecipientUtil.getEligibleForSending(possible);
    List<RecipientId> skipped  = Stream.of(SetUtil.difference(possible, eligible)).map(Recipient::getId).toList();

    return new GroupRecipientResult(eligible, skipped);
  }

  private static class GroupRecipientResult {
    private final List<Recipient>   target;
    private final List<RecipientId> skipped;

    private GroupRecipientResult(@NonNull List<Recipient> target, @NonNull List<RecipientId> skipped) {
      this.target  = target;
      this.skipped = skipped;
    }
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      String           raw     = data.getStringOrDefault(KEY_FILTER_RECIPIENTS, "");
      Set<RecipientId> filters = raw != null ? new HashSet<>(RecipientId.fromSerializedList(raw)) : Collections.emptySet();

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filters);
    }
  }
}

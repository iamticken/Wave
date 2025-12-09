package org.thoughtcrime.securesms.messages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.wave.core.util.logging.Log;
import org.wave.libwave.metadata.certificate.SenderCertificate;
import org.wave.libwave.protocol.InvalidKeyException;
import org.wave.libwave.protocol.InvalidRegistrationIdException;
import org.wave.libwave.protocol.NoSessionException;
import org.wave.libwave.zkgroup.groups.GroupSecretParams;
import org.wave.libwave.zkgroup.groupsend.GroupSendEndorsement;
import org.wave.libwave.zkgroup.groupsend.GroupSendFullToken;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.crypto.SenderKeyUtil;
import org.thoughtcrime.securesms.database.MessageSendLogTables;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.database.model.DistributionListId;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.model.GroupSendEndorsementRecords;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.WaveLocalMetrics;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.waveservice.api.WaveServiceMessageSender;
import org.whispersystems.waveservice.api.WaveServiceMessageSender.LegacyGroupEvents;
import org.whispersystems.waveservice.api.WaveServiceMessageSender.SenderKeyGroupEvents;
import org.whispersystems.waveservice.api.crypto.ContentHint;
import org.whispersystems.waveservice.api.crypto.SealedSenderAccess;
import org.whispersystems.waveservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.waveservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.waveservice.api.groupsv2.GroupSendEndorsements;
import org.whispersystems.waveservice.api.messages.SendMessageResult;
import org.whispersystems.waveservice.api.messages.WaveServiceDataMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceEditMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceStoryMessage;
import org.whispersystems.waveservice.api.messages.WaveServiceStoryMessageRecipient;
import org.whispersystems.waveservice.api.messages.WaveServiceTypingMessage;
import org.whispersystems.waveservice.api.messages.calls.WaveServiceCallMessage;
import org.whispersystems.waveservice.api.push.DistributionId;
import org.wave.core.models.ServiceId;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;
import org.whispersystems.waveservice.api.push.exceptions.NotFoundException;
import org.whispersystems.waveservice.api.util.Preconditions;
import org.whispersystems.waveservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;
import org.whispersystems.waveservice.internal.push.http.CancelationWave;
import org.whispersystems.waveservice.internal.push.http.PartialSendBatchCompleteListener;
import org.whispersystems.waveservice.internal.push.http.PartialSendCompleteListener;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class GroupSendUtil {

  private static final String TAG = Log.tag(GroupSendUtil.class);

  private GroupSendUtil() {}


  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * Messages sent this way, if failed to be decrypted by the receiving party, can be requested to be resent.
   * Note that the ContentHint <em>may not</em> be {@link ContentHint#RESENDABLE} -- it just means that we have an actual record of the message
   * and we <em>could</em> resend it if asked.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   * @param isForStory True if the message is related to a story, and should be sent with the story flag on the envelope
   */
  @WorkerThread
  public static List<SendMessageResult> sendResendableDataMessage(@NonNull Context context,
                                                                  @Nullable GroupId.V2 groupId,
                                                                  @Nullable DistributionListId distributionListId,
                                                                  @NonNull List<Recipient> allTargets,
                                                                  boolean isRecipientUpdate,
                                                                  ContentHint contentHint,
                                                                  @NonNull MessageId messageId,
                                                                  @NonNull WaveServiceDataMessage message,
                                                                  boolean urgent,
                                                                  boolean isForStory,
                                                                  @Nullable WaveServiceEditMessage editMessage)
      throws IOException, UntrustedIdentityException
  {
    Preconditions.checkArgument(groupId == null || distributionListId == null, "Cannot supply both a groupId and a distributionListId!");

    DistributionId distributionId = groupId != null ? getDistributionId(groupId) : getDistributionId(distributionListId);

    return sendMessage(context, groupId, distributionId, messageId, allTargets, isRecipientUpdate, isForStory, DataSendOperation.resendable(message, contentHint, messageId, urgent, isForStory, editMessage), null);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * Messages sent this way, if failed to be decrypted by the receiving party, can *not* be requested to be resent.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  public static List<SendMessageResult> sendUnresendableDataMessage(@NonNull Context context,
                                                                    @Nullable GroupId.V2 groupId,
                                                                    @NonNull List<Recipient> allTargets,
                                                                    boolean isRecipientUpdate,
                                                                    ContentHint contentHint,
                                                                    @NonNull WaveServiceDataMessage message,
                                                                    boolean urgent,
                                                                    CancelationWave cancelationWave)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), null, allTargets, isRecipientUpdate, false, DataSendOperation.unresendable(message, contentHint, urgent), cancelationWave);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   */
  @WorkerThread
  public static List<SendMessageResult> sendTypingMessage(@NonNull Context context,
                                                          @Nullable GroupId.V2 groupId,
                                                          @NonNull List<Recipient> allTargets,
                                                          @NonNull WaveServiceTypingMessage message,
                                                          @Nullable CancelationWave cancelationWave)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), null, allTargets, false, false, new TypingSendOperation(message), cancelationWave);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param groupId The groupId of the group you're sending to
   */
  @WorkerThread
  public static List<SendMessageResult> sendCallMessage(@NonNull Context context,
                                                        @NonNull GroupId.V2 groupId,
                                                        @NonNull List<Recipient> allTargets,
                                                        @NonNull WaveServiceCallMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), null, allTargets, false, false, new CallSendOperation(message), null);
  }

  /**
   * Handles all of the logic of sending a story to a distribution list. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  public static List<SendMessageResult> sendStoryMessage(@NonNull Context context,
                                                         @NonNull DistributionListId distributionListId,
                                                         @NonNull List<Recipient> allTargets,
                                                         boolean isRecipientUpdate,
                                                         @NonNull MessageId messageId,
                                                         long sentTimestamp,
                                                         @NonNull WaveServiceStoryMessage message,
                                                         @NonNull Set<WaveServiceStoryMessageRecipient> manifest)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(
        context,
        null,
        getDistributionId(distributionListId),
        messageId,
        allTargets,
        isRecipientUpdate,
        true,
        new StorySendOperation(messageId, null, sentTimestamp, message, manifest),
        null);
  }

  /**
   * Handles all of the logic of sending a story to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  public static List<SendMessageResult> sendGroupStoryMessage(@NonNull Context context,
                                                              @NonNull GroupId.V2 groupId,
                                                              @NonNull List<Recipient> allTargets,
                                                              boolean isRecipientUpdate,
                                                              @NonNull MessageId messageId,
                                                              long sentTimestamp,
                                                              @NonNull WaveServiceStoryMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(
        context,
        groupId,
        getDistributionId(groupId),
        messageId,
        allTargets,
        isRecipientUpdate,
        true,
        new StorySendOperation(messageId,
                               groupId,
                               sentTimestamp,
                               message,
                               allTargets.stream()
                                         .map(target -> new WaveServiceStoryMessageRecipient(new WaveServiceAddress(target.requireServiceId()),
                                                                                               Collections.emptyList(),
                                                                                               true))
                                         .collect(Collectors.toSet())),
        null);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  private static List<SendMessageResult> sendMessage(@NonNull Context context,
                                                     @Nullable GroupId.V2 groupId,
                                                     @Nullable DistributionId distributionId,
                                                     @Nullable MessageId relatedMessageId,
                                                     @NonNull List<Recipient> allTargets,
                                                     boolean isRecipientUpdate,
                                                     boolean isStorySend,
                                                     @NonNull SendOperation sendOperation,
                                                     @Nullable CancelationWave cancelationWave)
      throws IOException, UntrustedIdentityException
  {
    Log.i(TAG, "Starting group send. GroupId: " + (groupId != null ? groupId.toString() : "none") + ", DistributionId: " + (distributionId != null ? distributionId.toString() : "none") + " RelatedMessageId: " + (relatedMessageId != null ? relatedMessageId.toString() : "none") + ", Targets: " + allTargets.size() + ", RecipientUpdate: " + isRecipientUpdate + ", Operation: " + sendOperation.getClass().getSimpleName());

    Set<Recipient>  unregisteredTargets = allTargets.stream().filter(it -> it.isUnregistered() || it.isUnknown()).collect(Collectors.toSet());
    List<Recipient> registeredTargets   = allTargets.stream().filter(r -> !unregisteredTargets.contains(r)).collect(Collectors.toList());

    RecipientData               recipients                     = new RecipientData(context, registeredTargets, isStorySend);
    Optional<GroupRecord>       groupRecord                    = groupId != null ? WaveDatabase.groups().getGroup(groupId) : Optional.empty();
    GroupSendEndorsementRecords groupSendEndorsementRecords    = groupRecord.filter(GroupRecord::isV2Group).map(g -> WaveDatabase.groups().getGroupSendEndorsements(g.getId())).orElse(null);
    long                        groupSendEndorsementExpiration = groupRecord.map(GroupRecord::getGroupSendEndorsementExpiration).orElse(0L);
    SenderCertificate           senderCertificate              = SealedSenderAccessUtil.getSealedSenderCertificate();
    boolean                     useGroupSendEndorsements       = groupSendEndorsementRecords != null;

    if (useGroupSendEndorsements && senderCertificate == null) {
      Log.w(TAG, "Can't use group send endorsements without a sealed sender certificate, falling back to access key");
      useGroupSendEndorsements = false;
    } else if (useGroupSendEndorsements) {
      boolean refreshGroupSendEndorsements = false;

      if (groupSendEndorsementExpiration == 0) {
        Log.i(TAG, "No group send endorsements expiration set, need to refresh");
        refreshGroupSendEndorsements = true;
      } else if (groupSendEndorsementExpiration - TimeUnit.HOURS.toMillis(2) < System.currentTimeMillis()) {
        Log.i(TAG, "Group send endorsements are expired or expire imminently, refresh. Expires in " + (groupSendEndorsementExpiration - System.currentTimeMillis()) + "ms");
        refreshGroupSendEndorsements = true;
      } else if (groupSendEndorsementRecords.isMissingAnyEndorsements()) {
        Log.i(TAG, "Missing group send endorsements for some members, refresh.");
        refreshGroupSendEndorsements = true;
      }

      if (refreshGroupSendEndorsements) {
        try {
          GroupManager.updateGroupSendEndorsements(context, groupRecord.get().requireV2GroupProperties().getGroupMasterKey());

          groupSendEndorsementExpiration = WaveDatabase.groups().getGroupSendEndorsementsExpiration(groupId);
          groupSendEndorsementRecords    = WaveDatabase.groups().getGroupSendEndorsements(groupId);
        } catch (GroupChangeException | IOException e) {
          if (groupSendEndorsementExpiration == 0) {
            Log.w(TAG, "Unable to update group send endorsements, falling back to legacy", e);
            useGroupSendEndorsements = false;
            groupSendEndorsementRecords = new GroupSendEndorsementRecords(Collections.emptyMap());

            GroupSendEndorsementInternalNotifier.maybePostGroupSendFallbackError(context);
          } else {
            Log.w(TAG, "Unable to update group send endorsements, using what we have", e);
          }
        }

        Log.d(TAG, "Refresh all group state because we needed to refresh gse");
        AppDependencies.getJobManager().add(new RequestGroupV2InfoJob(groupId));
      }
    }

    List<Recipient> senderKeyTargets = new LinkedList<>();
    List<Recipient> legacyTargets    = new LinkedList<>();

    // Determine recipients that can be sent to via sender key vs must use legacy fan-out
    if (distributionId == null) {
      Log.i(TAG, "No DistributionId. Using legacy.");
      legacyTargets.addAll(registeredTargets);
    } else if (isStorySend) {
      Log.i(TAG, "Sending a story. Using sender key for all " + allTargets.size() + " recipients.");
      senderKeyTargets.addAll(registeredTargets);
    } else if (!useGroupSendEndorsements) {
      Log.i(TAG, "No group send endorsements, using legacy for all " + allTargets.size() + " recipients.");
      legacyTargets.addAll(registeredTargets);
    } else {
      for (Recipient recipient : registeredTargets) {
        boolean              validMembership      = groupRecord.get().getMembers().contains(recipient.getId());
        GroupSendEndorsement groupSendEndorsement = groupSendEndorsementRecords.getEndorsement(recipient.getId());

        if (groupSendEndorsement != null && recipient.getHasAci() && validMembership) {
          senderKeyTargets.add(recipient);
        } else {
          legacyTargets.add(recipient);
          if (validMembership) {
            Log.w(TAG, "Should be using group send endorsement but not found for " + recipient.getId());
            GroupSendEndorsementInternalNotifier.maybePostMissingGroupSendEndorsement(context);
          }
        }
      }
    }

    // Enforce minimum number of sender key destinations
    if (WaveStore.internal().getRemoveSenderKeyMinimum()) {
      Log.i(TAG, "Sender key minimum removed. Using for " + senderKeyTargets.size() + " recipients.");
    } else if (senderKeyTargets.size() < 2 && !isStorySend) {
      Log.i(TAG, "Too few sender-key-capable users (" + senderKeyTargets.size() + ") for non-story send. Doing all legacy sends.");
      legacyTargets.addAll(senderKeyTargets);
      senderKeyTargets.clear();
    } else {
      Log.i(TAG, "Can use sender key for " + senderKeyTargets.size() + "/" + allTargets.size() + " recipients.");
    }

    if (relatedMessageId != null && groupId != null) {
      WaveLocalMetrics.GroupMessageSend.onSenderKeyStarted(relatedMessageId.getId());
    }

    List<SendMessageResult>    allResults    = new ArrayList<>(allTargets.size());
    WaveServiceMessageSender messageSender = AppDependencies.getWaveServiceMessageSender();

    if (Util.hasItems(senderKeyTargets) && distributionId != null) {
      long           keyCreateTime  = SenderKeyUtil.getCreateTimeForOurKey(distributionId);
      long           keyAge         = System.currentTimeMillis() - keyCreateTime;

      if (keyCreateTime != -1 && keyAge > RemoteConfig.senderKeyMaxAge()) {
        Log.w(TAG, "DistributionId " + distributionId + " was created at " + keyCreateTime + " and is " + (keyAge) + " ms old (~" + TimeUnit.MILLISECONDS.toDays(keyAge) + " days). Rotating.");
        SenderKeyUtil.rotateOurKey(distributionId);
      }

      try {
        List<WaveServiceAddress>               targets               = new ArrayList<>(senderKeyTargets.size());
        List<UnidentifiedAccess>                 access                = new ArrayList<>(senderKeyTargets.size());
        Map<ServiceId.ACI, GroupSendEndorsement> senderKeyEndorsements = new HashMap<>(senderKeyTargets.size());
        GroupSendEndorsements                    groupSendEndorsements = null;

        for (Recipient recipient : senderKeyTargets) {
          targets.add(recipients.getAddress(recipient.getId()));

          if (useGroupSendEndorsements) {
            senderKeyEndorsements.put(recipient.requireAci(), groupSendEndorsementRecords.getEndorsement(recipient.getId()));
            access.add(recipients.getAccess(recipient.getId()));
          } else {
            access.add(recipients.requireAccess(recipient.getId()));
          }
        }

        if (useGroupSendEndorsements) {
          groupSendEndorsements = new GroupSendEndorsements(
              groupSendEndorsementExpiration,
              senderKeyEndorsements,
              senderCertificate,
              GroupSecretParams.deriveFromMasterKey(groupRecord.get().requireV2GroupProperties().getGroupMasterKey())
          );
        }

        final MessageSendLogTables messageLogDatabase  = WaveDatabase.messageLog();
        final AtomicLong           entryId             = new AtomicLong(-1);
        final boolean              includeInMessageLog = sendOperation.shouldIncludeInMessageLog();

        if (cancelationWave != null && cancelationWave.isCanceled()) {
          Log.i(TAG, "Send canceled before any sends took place. Returning an empty list.");
          return Collections.emptyList();
        }

        List<SendMessageResult> results = sendOperation.sendWithSenderKey(messageSender, distributionId, targets, access, groupSendEndorsements, isRecipientUpdate, partialResults -> {
          if (!includeInMessageLog) {
            return;
          }

          synchronized (entryId) {
            if (entryId.get() == -1) {
              entryId.set(messageLogDatabase.insertIfPossible(sendOperation.getSentTimestamp(), senderKeyTargets, partialResults, sendOperation.getContentHint(), sendOperation.getRelatedMessageId(), sendOperation.isUrgent()));
            } else {
              for (SendMessageResult result : partialResults) {
                entryId.set(messageLogDatabase.addRecipientToExistingEntryIfPossible(entryId.get(), recipients.requireRecipientId(result.getAddress()), sendOperation.getSentTimestamp(), result, sendOperation.getContentHint(), sendOperation.getRelatedMessageId(), sendOperation.isUrgent()));
              }
            }
          }
        });

        allResults.addAll(results);

        int successCount = (int) results.stream().filter(SendMessageResult::isSuccess).count();
        Log.d(TAG, "Successfully sent using sender key to " + successCount + "/" + targets.size() + " sender key targets.");

        if (relatedMessageId != null) {
          WaveLocalMetrics.GroupMessageSend.onSenderKeyMslInserted(relatedMessageId.getId());
        }
      } catch (InvalidUnidentifiedAccessHeaderException e) {
        Log.w(TAG, "Someone had a bad UD header. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);

        if (useGroupSendEndorsements) {
          GroupSendEndorsementInternalNotifier.maybePostGroupSendFallbackError(context);
        }
      } catch (NoSessionException e) {
        Log.w(TAG, "No session. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidKeyException e) {
        Log.w(TAG, "Invalid key. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidRegistrationIdException e) {
        Log.w(TAG, "Invalid registrationId. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (NotFoundException e) {
        Log.w(TAG, "Someone was unregistered. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      }
    } else if (relatedMessageId != null) {
      WaveLocalMetrics.GroupMessageSend.onSenderKeyShared(relatedMessageId.getId());
      WaveLocalMetrics.GroupMessageSend.onSenderKeyEncrypted(relatedMessageId.getId());
      WaveLocalMetrics.GroupMessageSend.onSenderKeyMessageSent(relatedMessageId.getId());
      WaveLocalMetrics.GroupMessageSend.onSenderKeySyncSent(relatedMessageId.getId());
      WaveLocalMetrics.GroupMessageSend.onSenderKeyMslInserted(relatedMessageId.getId());
    }

    if (cancelationWave != null && cancelationWave.isCanceled()) {
      Log.i(TAG, "Send canceled. Adding canceled results for " + legacyTargets.size() + " remaining legacy targets.");
      for (Recipient recipient : legacyTargets) {
        allResults.add(SendMessageResult.canceledFailure(recipients.getAddress(recipient.getId())));
      }

      if (unregisteredTargets.size() > 0) {
        List<SendMessageResult> unregisteredResults = unregisteredTargets.stream()
                                                                         .filter(Recipient::getHasServiceId)
                                                                         .map(t -> SendMessageResult.unregisteredFailure(new WaveServiceAddress(t.requireServiceId(), t.getE164().orElse(null))))
                                                                         .collect(Collectors.toList());
        allResults.addAll(unregisteredResults);
      }

      return allResults;
    }

    boolean onlyTargetIsSelfWithLinkedDevice = legacyTargets.isEmpty() && senderKeyTargets.isEmpty() && WaveStore.account().isMultiDevice();

    if (legacyTargets.size() > 0 || onlyTargetIsSelfWithLinkedDevice) {
      if (legacyTargets.size() > 0) {
        Log.i(TAG, "Need to do " + legacyTargets.size() + " legacy sends.");
      } else {
        Log.i(TAG, "Need to do a legacy send to send a sync message for a group of only ourselves.");
      }

      List<WaveServiceAddress> legacyTargetAddresses = legacyTargets.stream().map(r -> recipients.getAddress(r.getId())).collect(Collectors.toList());
      List<UnidentifiedAccess>   legacyTargetAccesses  = legacyTargets.stream().map(r -> recipients.getAccess(r.getId())).collect(Collectors.toList());
      List<GroupSendFullToken>   groupSendTokens       = null;
      boolean                    recipientUpdate       = isRecipientUpdate || allResults.size() > 0;

      if (useGroupSendEndorsements) {
        Instant           expiration        = Instant.ofEpochMilli(groupSendEndorsementExpiration);
        GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupRecord.get().requireV2GroupProperties().getGroupMasterKey());

        groupSendTokens = new ArrayList<>(legacyTargetAddresses.size());

        for (Recipient r : legacyTargets) {
          GroupSendEndorsement endorsement = groupSendEndorsementRecords.getEndorsement(r.getId());
          if (r.getHasAci() && endorsement != null) {
            groupSendTokens.add(endorsement.toFullToken(groupSecretParams, expiration));
          } else {
            groupSendTokens.add(null);
          }
        }
      }

      final MessageSendLogTables messageLogDatabase  = WaveDatabase.messageLog();
      final AtomicLong           entryId             = new AtomicLong(-1);
      final boolean              includeInMessageLog = sendOperation.shouldIncludeInMessageLog();

      List<SendMessageResult> results = sendOperation.sendLegacy(messageSender, legacyTargetAddresses, legacyTargets, SealedSenderAccess.forFanOutGroupSend(groupSendTokens, SealedSenderAccessUtil.getSealedSenderCertificate(), legacyTargetAccesses), recipientUpdate, result -> {
        if (!includeInMessageLog) {
          return;
        }

        synchronized (entryId) {
          if (entryId.get() == -1) {
            entryId.set(messageLogDatabase.insertIfPossible(recipients.requireRecipientId(result.getAddress()), sendOperation.getSentTimestamp(), result, sendOperation.getContentHint(), sendOperation.getRelatedMessageId(), sendOperation.isUrgent()));
          } else {
            entryId.set(messageLogDatabase.addRecipientToExistingEntryIfPossible(entryId.get(), recipients.requireRecipientId(result.getAddress()), sendOperation.getSentTimestamp(), result, sendOperation.getContentHint(), sendOperation.getRelatedMessageId(), sendOperation.isUrgent()));
          }
        }
      }, cancelationWave);

      allResults.addAll(results);

      int successCount = (int) results.stream().filter(SendMessageResult::isSuccess).count();
      Log.d(TAG, "Successfully sent using 1:1 to " + successCount + "/" + legacyTargetAddresses.size() + " legacy targets.");
    } else if (relatedMessageId != null) {
      WaveLocalMetrics.GroupMessageSend.onLegacyMessageSent(relatedMessageId.getId());
      WaveLocalMetrics.GroupMessageSend.onLegacySyncFinished(relatedMessageId.getId());
    }

    if (unregisteredTargets.size() > 0) {
      Log.w(TAG, "There are " + unregisteredTargets.size() + " unregistered targets. Including failure results.");

      List<SendMessageResult> unregisteredResults = unregisteredTargets.stream()
                                                                       .filter(Recipient::getHasServiceId)
                                                                       .map(t -> SendMessageResult.unregisteredFailure(new WaveServiceAddress(t.requireServiceId(), t.getE164().orElse(null))))
                                                                       .collect(Collectors.toList());

      if (unregisteredResults.size() < unregisteredTargets.size()) {
        Log.w(TAG, "There are " + (unregisteredTargets.size() - unregisteredResults.size()) + " targets that have no UUID! Cannot report a failure for them.");
      }

      allResults.addAll(unregisteredResults);
    }

    return allResults;
  }

  private static @Nullable DistributionId getDistributionId(@Nullable GroupId.V2 groupId) {
    if (groupId != null) {
      return WaveDatabase.groups().getOrCreateDistributionId(groupId);
    } else {
      return null;
    }
  }

  private static @Nullable DistributionId getDistributionId(@Nullable DistributionListId distributionListId) {
    if (distributionListId != null) {
      return Optional.ofNullable(WaveDatabase.distributionLists().getDistributionId(distributionListId)).orElse(null);
    } else {
      return null;
    }
  }

  /** Abstraction layer to handle the different types of message send operations we can do */
  private interface SendOperation {
    @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull WaveServiceMessageSender messageSender,
                                                       @NonNull DistributionId distributionId,
                                                       @NonNull List<WaveServiceAddress> targets,
                                                       @NonNull List<UnidentifiedAccess> access,
                                                       @Nullable GroupSendEndorsements groupSendEndorsements,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendBatchCompleteListener partialListener)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException;

    @NonNull List<SendMessageResult> sendLegacy(@NonNull WaveServiceMessageSender messageSender,
                                                @NonNull List<WaveServiceAddress> targets,
                                                @NonNull List<Recipient> targetRecipients,
                                                @NonNull List<SealedSenderAccess> sealedSenderAccesses,
                                                boolean isRecipientUpdate,
                                                @Nullable PartialSendCompleteListener partialListener,
                                                @Nullable CancelationWave cancelationWave)
        throws IOException, UntrustedIdentityException;

    @NonNull ContentHint getContentHint();
    long getSentTimestamp();
    boolean shouldIncludeInMessageLog();
    @NonNull MessageId getRelatedMessageId();
    boolean isUrgent();
  }

  private static class DataSendOperation implements SendOperation {
    private final WaveServiceDataMessage message;
    private final ContentHint              contentHint;
    private final MessageId                relatedMessageId;
    private final boolean                  resendable;
    private final boolean                  urgent;
    private final boolean                  isForStory;
    private final WaveServiceEditMessage editMessage;

    public static DataSendOperation resendable(@NonNull WaveServiceDataMessage message, @NonNull ContentHint contentHint, @NonNull MessageId relatedMessageId, boolean urgent, boolean isForStory, @Nullable WaveServiceEditMessage editMessage) {
      return new DataSendOperation(editMessage != null ? editMessage.getDataMessage() : message, contentHint, true, relatedMessageId, urgent, isForStory, editMessage);
    }

    public static DataSendOperation unresendable(@NonNull WaveServiceDataMessage message, @NonNull ContentHint contentHint, boolean urgent) {
      return new DataSendOperation(message, contentHint, false, null, urgent, false, null);
    }

    private DataSendOperation(@NonNull WaveServiceDataMessage message, @NonNull ContentHint contentHint, boolean resendable, @Nullable MessageId relatedMessageId, boolean urgent, boolean isForStory, @Nullable WaveServiceEditMessage editMessage) {
      this.message          = message;
      this.contentHint      = contentHint;
      this.resendable       = resendable;
      this.relatedMessageId = relatedMessageId;
      this.urgent           = urgent;
      this.isForStory       = isForStory;
      this.editMessage      = editMessage;

      if (resendable && relatedMessageId == null) {
        throw new IllegalArgumentException("If a message is resendable, it must have a related message ID!");
      }
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull WaveServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<WaveServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              @Nullable GroupSendEndorsements groupSendEndorsements,
                                                              boolean isRecipientUpdate,
                                                              @Nullable PartialSendBatchCompleteListener partialListener)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      SenderKeyGroupEvents listener = relatedMessageId != null ? new SenderKeyMetricEventListener(relatedMessageId.getId()) : SenderKeyGroupEvents.EMPTY;
      return messageSender.sendGroupDataMessage(distributionId, targets, access, groupSendEndorsements, isRecipientUpdate, contentHint, message, listener, urgent, isForStory, editMessage, partialListener);
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull WaveServiceMessageSender messageSender,
                                                       @NonNull List<WaveServiceAddress> targets,
                                                       @NonNull List<Recipient> targetRecipients,
                                                       @NonNull List<SealedSenderAccess> sealedSenderAccesses,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationWave cancelationWave)
        throws IOException, UntrustedIdentityException
    {
      // PniSignatures are only needed for 1:1 messages, but some message jobs use the GroupSendUtil methods to send 1:1
      if (targets.size() == 1 && relatedMessageId == null) {
        Recipient          targetRecipient    = targetRecipients.get(0);
        SealedSenderAccess sealedSenderAccess = sealedSenderAccesses.get(0);
        SendMessageResult  result;

        try {
          if (editMessage != null) {
            result = messageSender.sendEditMessage(targets.get(0), sealedSenderAccess, contentHint, message, WaveServiceMessageSender.IndividualSendEvents.EMPTY, urgent, editMessage.getTargetSentTimestamp());
          } else {
            result = messageSender.sendDataMessage(targets.get(0), sealedSenderAccess, contentHint, message, WaveServiceMessageSender.IndividualSendEvents.EMPTY, urgent, targetRecipient.getNeedsPniSignature());
          }
        } catch (IOException e) {
          result = WaveServiceMessageSender.mapSendErrorToSendResult(e, message.getTimestamp(), targets.get(0));
        }

        if (result.isSuccess() && targetRecipient.getNeedsPniSignature()) {
          WaveDatabase.pendingPniSignatureMessages().insertIfNecessary(targetRecipients.get(0).getId(), getSentTimestamp(), result);
        }

        return Collections.singletonList(result);
      } else {
        LegacyGroupEvents listener = relatedMessageId != null ? new LegacyMetricEventListener(relatedMessageId.getId()) : LegacyGroupEvents.EMPTY;

        if (editMessage != null) {
          return messageSender.sendEditMessage(targets, sealedSenderAccesses, isRecipientUpdate, contentHint, message, listener, partialListener, cancelationWave, urgent, editMessage.getTargetSentTimestamp());
        } else {
          return messageSender.sendDataMessage(targets, sealedSenderAccesses, isRecipientUpdate, contentHint, message, listener, partialListener, cancelationWave, urgent);
        }
      }
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return contentHint;
    }

    @Override
    public long getSentTimestamp() {
      return message.getTimestamp();
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return resendable;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      if (relatedMessageId != null) {
        return relatedMessageId;
      } else {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public boolean isUrgent() {
      return urgent;
    }
  }

  private static class TypingSendOperation implements SendOperation {

    private final WaveServiceTypingMessage message;

    private TypingSendOperation(@NonNull WaveServiceTypingMessage message) {
      this.message = message;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull WaveServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<WaveServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              @Nullable GroupSendEndorsements groupSendEndorsements,
                                                              boolean isRecipientUpdate,
                                                              @Nullable PartialSendBatchCompleteListener partialListener)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      Preconditions.checkNotNull(groupSendEndorsements, "GSEs must be non-null for non-story sender key send.");

      messageSender.sendGroupTyping(distributionId, targets, access, groupSendEndorsements, message);
      List<SendMessageResult> results = targets.stream().map(a -> SendMessageResult.success(a, Collections.emptyList(), true, false, -1, Optional.empty())).collect(Collectors.toList());

      if (partialListener != null) {
        partialListener.onPartialSendComplete(results);
      }

      return results;
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull WaveServiceMessageSender messageSender,
                                                       @NonNull List<WaveServiceAddress> targets,
                                                       @NonNull List<Recipient> targetRecipients,
                                                       @NonNull List<SealedSenderAccess> sealedSenderAccesses,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationWave cancelationWave)
        throws IOException
    {
      messageSender.sendTyping(targets, sealedSenderAccesses, message, cancelationWave);
      return targets.stream().map(a -> SendMessageResult.success(a, Collections.emptyList(), true, false, -1, Optional.empty())).collect(Collectors.toList());
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return ContentHint.IMPLICIT;
    }

    @Override
    public long getSentTimestamp() {
      return message.getTimestamp();
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return false;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUrgent() {
      return false;
    }
  }

  private static class CallSendOperation implements SendOperation {

    private final WaveServiceCallMessage message;

    private CallSendOperation(@NonNull WaveServiceCallMessage message) {
      this.message = message;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull WaveServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<WaveServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              @Nullable GroupSendEndorsements groupSendEndorsements,
                                                              boolean isRecipientUpdate,
                                                              @Nullable PartialSendBatchCompleteListener partialSendListener)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      Preconditions.checkNotNull(groupSendEndorsements, "GSEs must be non-null for non-story sender key send.");

      return messageSender.sendCallMessage(distributionId, targets, access, groupSendEndorsements, message, partialSendListener);
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull WaveServiceMessageSender messageSender,
                                                       @NonNull List<WaveServiceAddress> targets,
                                                       @NonNull List<Recipient> targetRecipients,
                                                       @NonNull List<SealedSenderAccess> sealedSenderAccesses,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationWave cancelationWave)
        throws IOException
    {
      return messageSender.sendCallMessage(targets, sealedSenderAccesses, message);
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return ContentHint.IMPLICIT;
    }

    @Override
    public long getSentTimestamp() {
      return message.getTimestamp().get();
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return false;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUrgent() {
      return message.isUrgent();
    }
  }

  public static class StorySendOperation implements SendOperation {

    private final MessageId                               relatedMessageId;
    private final GroupId                                 groupId;
    private final long                                    sentTimestamp;
    private final WaveServiceStoryMessage               message;
    private final Set<WaveServiceStoryMessageRecipient> manifest;

    public StorySendOperation(@NonNull MessageId relatedMessageId,
                              @Nullable GroupId groupId,
                              long sentTimestamp,
                              @NonNull WaveServiceStoryMessage message,
                              @NonNull Set<WaveServiceStoryMessageRecipient> manifest)
    {
      this.relatedMessageId = relatedMessageId;
      this.groupId          = groupId;
      this.sentTimestamp    = sentTimestamp;
      this.message          = message;
      this.manifest         = manifest;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull WaveServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<WaveServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              @Nullable GroupSendEndorsements groupSendEndorsements,
                                                              boolean isRecipientUpdate,
                                                              @Nullable PartialSendBatchCompleteListener partialListener)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      return messageSender.sendGroupStory(distributionId, Optional.ofNullable(groupId).map(GroupId::getDecodedId), targets, access, groupSendEndorsements, isRecipientUpdate, message, getSentTimestamp(), manifest, partialListener);
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull WaveServiceMessageSender messageSender,
                                                       @NonNull List<WaveServiceAddress> targets,
                                                       @NonNull List<Recipient> targetRecipients,
                                                       @NonNull List<SealedSenderAccess> sealedSenderAccesses,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationWave cancelationWave)
        throws IOException, UntrustedIdentityException
    {
      // We only allow legacy sends if you're sending to an empty group and just need to send a sync message.
      if (targets.isEmpty()) {
        Log.w(TAG, "Only sending a sync message.");
        messageSender.sendStorySyncMessage(message, getSentTimestamp(), isRecipientUpdate, manifest);
        return Collections.emptyList();
      } else {
        throw new UnsupportedOperationException("Stories can only be send via sender key!");
      }
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return ContentHint.IMPLICIT;
    }

    @Override
    public long getSentTimestamp() {
      return sentTimestamp;
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return true;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      return relatedMessageId;
    }

    @Override
    public boolean isUrgent() {
      return false;
    }
  }

  private static final class SenderKeyMetricEventListener implements SenderKeyGroupEvents {

    private final long messageId;

    private SenderKeyMetricEventListener(long messageId) {
      this.messageId = messageId;
    }

    @Override
    public void onSenderKeyShared() {
      WaveLocalMetrics.GroupMessageSend.onSenderKeyShared(messageId);
    }

    @Override
    public void onMessageEncrypted() {
      WaveLocalMetrics.GroupMessageSend.onSenderKeyEncrypted(messageId);
    }

    @Override
    public void onMessageSent() {
      WaveLocalMetrics.GroupMessageSend.onSenderKeyMessageSent(messageId);
    }

    @Override
    public void onSyncMessageSent() {
      WaveLocalMetrics.GroupMessageSend.onSenderKeySyncSent(messageId);
    }
  }

  private static final class LegacyMetricEventListener implements LegacyGroupEvents {

    private final long messageId;

    private LegacyMetricEventListener(long messageId) {
      this.messageId = messageId;
    }

    @Override
    public void onMessageEncrypted() {}

    @Override
    public void onMessageSent() {
      WaveLocalMetrics.GroupMessageSend.onLegacyMessageSent(messageId);
    }

    @Override
    public void onSyncMessageSent() {
      WaveLocalMetrics.GroupMessageSend.onLegacySyncFinished(messageId);
    }
  }

  /**
   * Little utility wrapper that lets us get the various different slices of recipient models that we need for different methods.
   */
  private static final class RecipientData {

    private final Map<RecipientId, Optional<UnidentifiedAccess>> accessById;
    private final Map<RecipientId, WaveServiceAddress>             addressById;
    private final RecipientAccessList                                accessList;

    RecipientData(@NonNull Context context, @NonNull List<Recipient> recipients, boolean isForStory) throws IOException {
      this.accessById  = SealedSenderAccessUtil.getAccessMapFor(recipients, isForStory);
      this.addressById = mapAddresses(context, recipients);
      this.accessList  = new RecipientAccessList(recipients);
    }

    @NonNull WaveServiceAddress getAddress(@NonNull RecipientId id) {
      return Objects.requireNonNull(addressById.get(id));
    }

    @NonNull Optional<UnidentifiedAccess> getAccessPair(@NonNull RecipientId id) {
      return Objects.requireNonNull(accessById.get(id));
    }

    @Nullable UnidentifiedAccess getAccess(@NonNull RecipientId id) {
      return Objects.requireNonNull(accessById.get(id)).orElse(null);
    }

    @NonNull UnidentifiedAccess requireAccess(@NonNull RecipientId id) {
      return Objects.requireNonNull(accessById.get(id)).get();
    }

    @NonNull RecipientId requireRecipientId(@NonNull WaveServiceAddress address) {
      return accessList.requireIdByAddress(address);
    }

    private static @NonNull Map<RecipientId, WaveServiceAddress> mapAddresses(@NonNull Context context, @NonNull List<Recipient> recipients) throws IOException {
      List<WaveServiceAddress> addresses = RecipientUtil.toWaveServiceAddressesFromResolved(context, recipients);

      Iterator<Recipient>            recipientIterator = recipients.iterator();
      Iterator<WaveServiceAddress> addressIterator   = addresses.iterator();

      Map<RecipientId, WaveServiceAddress> map = new HashMap<>(recipients.size());

      while (recipientIterator.hasNext()) {
        map.put(recipientIterator.next().getId(), addressIterator.next());
      }

      return map;
    }
  }
}

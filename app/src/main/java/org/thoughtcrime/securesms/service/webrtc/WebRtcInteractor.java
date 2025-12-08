package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.wave.ringrtc.CallId;
import org.wave.ringrtc.CallManager;
import org.wave.ringrtc.GroupCall;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCommand;
import org.thoughtcrime.securesms.webrtc.audio.WaveAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.waveservice.api.messages.calls.WaveServiceCallMessage;

import java.util.Collection;
import java.util.UUID;

/**
 * Serves as the bridge between the action processing framework as the WebRTC service. Attempts
 * to minimize direct access to various managers by providing a simple proxy to them. Due to the
 * heavy use of {@link CallManager} throughout, it was exempted from the rule.
 */
public class WebRtcInteractor {

  private final Context                        context;
  private final WaveCallManager              waveCallManager;
  private final LockManager                    lockManager;
  private final CameraEventListener            cameraEventListener;
  private final GroupCall.Observer             groupCallObserver;
  private final AppForegroundObserver.Listener foregroundListener;

  public WebRtcInteractor(@NonNull Context context,
                          @NonNull WaveCallManager waveCallManager,
                          @NonNull LockManager lockManager,
                          @NonNull CameraEventListener cameraEventListener,
                          @NonNull GroupCall.Observer groupCallObserver,
                          @NonNull AppForegroundObserver.Listener foregroundListener)
  {
    this.context             = context;
    this.waveCallManager   = waveCallManager;
    this.lockManager         = lockManager;
    this.cameraEventListener = cameraEventListener;
    this.groupCallObserver   = groupCallObserver;
    this.foregroundListener  = foregroundListener;
  }

  @NonNull Context getContext() {
    return context;
  }

  @NonNull CameraEventListener getCameraEventListener() {
    return cameraEventListener;
  }

  @NonNull CallManager getCallManager() {
    return waveCallManager.getRingRtcCallManager();
  }

  @NonNull GroupCall.Observer getGroupCallObserver() {
    return groupCallObserver;
  }

  @NonNull AppForegroundObserver.Listener getForegroundListener() {
    return foregroundListener;
  }

  void updatePhoneState(@NonNull LockManager.PhoneState phoneState) {
    lockManager.updatePhoneState(phoneState);
  }

  void postStateUpdate(@NonNull WebRtcServiceState state) {
    waveCallManager.postStateUpdate(state);
  }

  void sendCallMessage(@NonNull RemotePeer remotePeer, @NonNull WaveServiceCallMessage callMessage) {
    waveCallManager.sendCallMessage(remotePeer, callMessage);
  }

  void sendGroupCallMessage(@NonNull Recipient recipient, @Nullable String groupCallEraId, @Nullable CallId callId, boolean isIncoming, boolean isJoinEvent) {
    waveCallManager.sendGroupCallUpdateMessage(recipient, groupCallEraId, callId, isIncoming, isJoinEvent);
  }

  void updateGroupCallUpdateMessage(@NonNull RecipientId groupId, @Nullable String groupCallEraId, @NonNull Collection<UUID> joinedMembers, boolean isCallFull) {
    waveCallManager.updateGroupCallUpdateMessage(groupId, groupCallEraId, joinedMembers, isCallFull);
  }

  void setCallInProgressNotification(int type, @NonNull RemotePeer remotePeer, boolean isVideoCall) {
    ActiveCallManager.update(context, type, remotePeer.getRecipient().getId(), isVideoCall);
  }

  void setCallInProgressNotification(int type, @NonNull Recipient recipient, boolean isVideoCall) {
    ActiveCallManager.update(context, type, recipient.getId(), isVideoCall);
  }

  void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    waveCallManager.retrieveTurnServers(remotePeer);
  }

  void stopForegroundService() {
    ActiveCallManager.stop();
  }

  void insertMissedCall(@NonNull RemotePeer remotePeer, long timestamp, boolean isVideoOffer) {
    insertMissedCall(remotePeer, timestamp, isVideoOffer, CallTable.Event.MISSED);
  }

  void insertMissedCall(@NonNull RemotePeer remotePeer, long timestamp, boolean isVideoOffer, @NonNull CallTable.Event missedEvent) {
    waveCallManager.insertMissedCall(remotePeer, timestamp, isVideoOffer, missedEvent);
  }

  void insertReceivedCall(@NonNull RemotePeer remotePeer, boolean isVideoOffer) {
    waveCallManager.insertReceivedCall(remotePeer, isVideoOffer);
  }

  boolean startWebRtcCallActivityIfPossible() {
    return waveCallManager.startCallCardActivityIfPossible();
  }

  void registerPowerButtonReceiver() {
    ActiveCallManager.changePowerButtonReceiver(context, true);
  }

  void unregisterPowerButtonReceiver() {
    ActiveCallManager.changePowerButtonReceiver(context, false);
  }

  void silenceIncomingRinger() {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.SilenceIncomingRinger());
  }

  void initializeAudioForCall() {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.Initialize());
  }

  void startIncomingRinger(@Nullable Uri ringtoneUri, boolean vibrate) {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.StartIncomingRinger(ringtoneUri, vibrate));
  }

  void startOutgoingRinger() {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.StartOutgoingRinger());
  }

  void stopAudio(boolean playDisconnect) {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.Stop(playDisconnect));
  }

  void startAudioCommunication() {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.Start());
  }

  public void setUserAudioDevice(@Nullable RecipientId recipientId, @NonNull WaveAudioManager.ChosenAudioDeviceIdentifier userDevice) {
    if (userDevice.isLegacy()) {
      ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.SetUserDevice(recipientId, userDevice.getDesiredAudioDeviceLegacy().ordinal(), false));
    } else {
      ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.SetUserDevice(recipientId, userDevice.getDesiredAudioDevice31(), true));
    }
  }

  public void setDefaultAudioDevice(@NonNull RecipientId recipientId, @NonNull WaveAudioManager.AudioDevice userDevice, boolean clearUserEarpieceSelection) {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.SetDefaultDevice(recipientId, userDevice, clearUserEarpieceSelection));
  }

  public void playStateChangeUp() {
    ActiveCallManager.sendAudioManagerCommand(context, new AudioManagerCommand.PlayStateChangeUp());
  }

  void peekGroupCallForRingingCheck(@NonNull GroupCallRingCheckInfo groupCallRingCheckInfo) {
    waveCallManager.peekGroupCallForRingingCheck(groupCallRingCheckInfo);
  }

  public void activateCall(RecipientId recipientId) {
    AndroidTelecomUtil.activateCall(recipientId);
  }

  public void terminateCall(RecipientId recipientId) {
    AndroidTelecomUtil.terminateCall(recipientId);
  }

  public boolean addNewIncomingCall(RecipientId recipientId, long callId, boolean remoteVideoOffer) {
    return AndroidTelecomUtil.addIncomingCall(recipientId, callId, remoteVideoOffer);
  }

  public void rejectIncomingCall(RecipientId recipientId) {
    AndroidTelecomUtil.reject(recipientId);
  }

  public boolean addNewOutgoingCall(RecipientId recipientId, long callId, boolean isVideoCall) {
    return AndroidTelecomUtil.addOutgoingCall(recipientId, callId, isVideoCall);
  }

  public void requestGroupMembershipProof(GroupId.V2 groupId, int groupCallHashCode) {
    waveCallManager.requestGroupMembershipToken(groupId, groupCallHashCode);
  }

  public void sendAcceptedCallEventSyncMessage(@NonNull RemotePeer remotePeer, boolean isOutgoing, boolean isVideoCall) {
    waveCallManager.sendAcceptedCallEventSyncMessage(remotePeer, isOutgoing, isVideoCall);
  }

  public void sendNotAcceptedCallEventSyncMessage(@NonNull RemotePeer remotePeer, boolean isOutgoing, boolean isVideoCall) {
    waveCallManager.sendNotAcceptedCallEventSyncMessage(remotePeer, isOutgoing, isVideoCall);
  }

  public void sendGroupCallNotAcceptedCallEventSyncMessage(@NonNull RemotePeer remotePeer, boolean isOutgoing) {
    waveCallManager.sendGroupCallNotAcceptedCallEventSyncMessage(remotePeer, isOutgoing);
  }
}

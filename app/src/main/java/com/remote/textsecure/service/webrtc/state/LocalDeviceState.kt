package org.thoughtcrime.securesms.service.webrtc.state

import org.thoughtcrime.securesms.components.sensors.Orientation
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.thoughtcrime.securesms.webrtc.audio.WaveAudioManager
import org.webrtc.PeerConnection

/**
 * Local device specific state.
 */
data class LocalDeviceState(
  var cameraState: CameraState = CameraState.UNKNOWN,
  var isMicrophoneEnabled: Boolean = true,
  var orientation: Orientation = Orientation.PORTRAIT_BOTTOM_EDGE,
  var isLandscapeEnabled: Boolean = false,
  var deviceOrientation: Orientation = Orientation.PORTRAIT_BOTTOM_EDGE,
  var activeDevice: WaveAudioManager.AudioDevice = WaveAudioManager.AudioDevice.NONE,
  var availableDevices: Set<WaveAudioManager.AudioDevice> = emptySet(),
  var bluetoothPermissionDenied: Boolean = false,
  var networkConnectionType: PeerConnection.AdapterType = PeerConnection.AdapterType.UNKNOWN,
  var handRaisedTimestamp: Long = CallParticipant.HAND_LOWERED,
  var remoteMutedBy: CallParticipant? = null
) {

  fun duplicate(): LocalDeviceState {
    return copy()
  }
}

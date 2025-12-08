/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.waveservice.api;

import org.wave.libwave.net.Network;
import org.whispersystems.waveservice.api.account.AccountApi;
import org.whispersystems.waveservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.waveservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.waveservice.api.groupsv2.GroupsV2Operations;
import org.wave.core.models.ServiceId.ACI;
import org.wave.core.models.ServiceId.PNI;
import org.whispersystems.waveservice.api.registration.RegistrationApi;
import org.whispersystems.waveservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.waveservice.api.svr.SecureValueRecoveryV3;
import org.whispersystems.waveservice.api.websocket.WaveWebSocket;
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration;
import org.whispersystems.waveservice.internal.push.PushServiceSocket;
import org.whispersystems.waveservice.internal.push.WhoAmIResponse;
import org.whispersystems.waveservice.internal.util.StaticCredentialsProvider;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The main interface for creating, registering, and
 * managing a Wave Service account.
 *
 * @author Moxie Marlinspike
 */
public class WaveServiceAccountManager {

  private static final String TAG = WaveServiceAccountManager.class.getSimpleName();

  private final PushServiceSocket                      pushServiceSocket;
  private final GroupsV2Operations                     groupsV2Operations;
  private final WaveServiceConfiguration             configuration;
  private final WaveWebSocket.AuthenticatedWebSocket authWebSocket;
  private final AccountApi                             accountApi;

  /**
   * Construct a WaveServiceAccountManager.
   * @param configuration The URL for the Wave Service.
   * @param aci The Wave Service ACI.
   * @param pni The Wave Service PNI.
   * @param e164 The Wave Service phone number.
   * @param password A Wave Service password.
   * @param waveAgent A string which identifies the client software.
   */
  public static WaveServiceAccountManager createWithStaticCredentials(WaveServiceConfiguration configuration,
                                                                        ACI aci,
                                                                        PNI pni,
                                                                        String e164,
                                                                        int deviceId,
                                                                        String password,
                                                                        String waveAgent,
                                                                        boolean automaticNetworkRetry,
                                                                        int maxGroupSize)
  {
    StaticCredentialsProvider credentialProvider = new StaticCredentialsProvider(aci, pni, e164, deviceId, password);
    GroupsV2Operations        gv2Operations      = new GroupsV2Operations(ClientZkOperations.create(configuration), maxGroupSize);

    return new WaveServiceAccountManager(
        null,
        null,
        new PushServiceSocket(configuration, credentialProvider, waveAgent, automaticNetworkRetry),
        gv2Operations
    );
  }

  public WaveServiceAccountManager(@Nullable WaveWebSocket.AuthenticatedWebSocket authWebSocket,
                                     @Nullable AccountApi accountApi,
                                     @Nonnull PushServiceSocket pushServiceSocket,
                                     @Nonnull GroupsV2Operations groupsV2Operations) {
    this.authWebSocket      = authWebSocket;
    this.accountApi         = accountApi;
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = pushServiceSocket;
    this.configuration      = pushServiceSocket.getConfiguration();
  }

  public SecureValueRecoveryV2 getSecureValueRecoveryV2(String mrEnclave) {
    return new SecureValueRecoveryV2(configuration, mrEnclave, authWebSocket);
  }

  public SecureValueRecoveryV3 getSecureValueRecoveryV3(Network network) {
    return new SecureValueRecoveryV3(network, authWebSocket);
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return NetworkResultUtil.toBasicLegacy(accountApi.whoAmI());
  }

  /**
   * Request a push challenge. A number will be pushed to the GCM (FCM) id. This can then be used
   * during SMS/call requests to bypass the CAPTCHA.
   *
   * @param gcmRegistrationId The GCM (FCM) id to use.
   * @param sessionId         The session to request a push for.
   * @throws IOException
   */
  public void requestRegistrationPushChallenge(String sessionId, String gcmRegistrationId) throws IOException {
    pushServiceSocket.requestPushChallenge(sessionId, gcmRegistrationId);
  }

  public void checkNetworkConnection() throws IOException {
    this.pushServiceSocket.pingStorageService();
  }

  public void cancelInFlightRequests() {
    this.pushServiceSocket.cancelInFlightRequests();
  }

  public GroupsV2Api getGroupsV2Api() {
    return new GroupsV2Api(authWebSocket, pushServiceSocket, groupsV2Operations);
  }

  public RegistrationApi getRegistrationApi() {
    return new RegistrationApi(pushServiceSocket);
  }
}

package org.whispersystems.waveservice.api.groupsv2;

import org.wave.libwave.zkgroup.ServerPublicParams;
import org.wave.libwave.zkgroup.ServerSecretParams;
import org.wave.libwave.zkgroup.VerificationFailedException;
import org.wave.libwave.zkgroup.groups.GroupPublicParams;
import org.wave.libwave.zkgroup.profiles.ExpiringProfileKeyCredentialResponse;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCommitment;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.wave.libwave.zkgroup.profiles.ServerZkProfileOperations;
import org.wave.core.models.ServiceId.ACI;
import org.whispersystems.waveservice.testutil.LibWaveLibraryUtil;

import java.time.Instant;

/**
 * Provides Zk group operations that the server would provide.
 */
final class TestZkGroupServer {

  private final ServerPublicParams        serverPublicParams;
  private final ServerZkProfileOperations serverZkProfileOperations;

  TestZkGroupServer() {
    LibWaveLibraryUtil.assumeLibWaveSupportedOnOS();

    ServerSecretParams serverSecretParams = ServerSecretParams.generate();

    serverPublicParams        = serverSecretParams.getPublicParams();
    serverZkProfileOperations = new ServerZkProfileOperations(serverSecretParams);
  }

  public ServerPublicParams getServerPublicParams() {
    return serverPublicParams;
  }

  public ExpiringProfileKeyCredentialResponse getExpiringProfileKeyCredentialResponse(ProfileKeyCredentialRequest request, ACI aci, ProfileKeyCommitment commitment, Instant expiration) throws VerificationFailedException {
    return serverZkProfileOperations.issueExpiringProfileKeyCredential(request, aci.getLibWaveAci(), commitment, expiration);
  }

  public void assertProfileKeyCredentialPresentation(GroupPublicParams publicParams, ProfileKeyCredentialPresentation profileKeyCredentialPresentation, Instant now) {
    try {
      serverZkProfileOperations.verifyProfileKeyCredentialPresentation(publicParams, profileKeyCredentialPresentation, now);
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }
  }
}

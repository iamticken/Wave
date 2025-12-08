package org.thoughtcrime.securesms;

import org.wave.libwave.zkgroup.ServerPublicParams;
import org.wave.libwave.zkgroup.ServerSecretParams;
import org.wave.libwave.zkgroup.VerificationFailedException;
import org.wave.libwave.zkgroup.groups.GroupPublicParams;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCommitment;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.wave.libwave.zkgroup.profiles.ServerZkProfileOperations;
import org.whispersystems.waveservice.test.LibWaveLibraryUtil;

import java.util.UUID;

/**
 * Provides Zk group operations that the server would provide.
 * Copied in app from libwave
 */
public final class TestZkGroupServer {

  private final ServerPublicParams        serverPublicParams;
  private final ServerZkProfileOperations serverZkProfileOperations;

  public TestZkGroupServer() {
    LibWaveLibraryUtil.assumeLibWaveSupportedOnOS();

    ServerSecretParams serverSecretParams = ServerSecretParams.generate();

    serverPublicParams        = serverSecretParams.getPublicParams();
    serverZkProfileOperations = new ServerZkProfileOperations(serverSecretParams);
  }

  public ServerPublicParams getServerPublicParams() {
    return serverPublicParams;
  }
}

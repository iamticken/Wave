package org.whispersystems.waveservice.api.groupsv2;

import org.wave.libwave.zkgroup.InvalidInputException;
import org.wave.libwave.zkgroup.ServerPublicParams;
import org.wave.libwave.zkgroup.auth.ClientZkAuthOperations;
import org.wave.libwave.zkgroup.profiles.ClientZkProfileOperations;
import org.wave.libwave.zkgroup.receipts.ClientZkReceiptOperations;
import org.whispersystems.waveservice.internal.configuration.WaveServiceConfiguration;

/**
 * Contains access to all ZK group operations for the client.
 * <p>
 * Authorization and profile operations.
 */
public final class ClientZkOperations {

  private final ClientZkAuthOperations    clientZkAuthOperations;
  private final ClientZkProfileOperations clientZkProfileOperations;
  private final ClientZkReceiptOperations clientZkReceiptOperations;
  private final ServerPublicParams        serverPublicParams;

  public ClientZkOperations(ServerPublicParams serverPublicParams) {
    this.serverPublicParams        = serverPublicParams;
    this.clientZkAuthOperations    = new ClientZkAuthOperations   (serverPublicParams);
    this.clientZkProfileOperations = new ClientZkProfileOperations(serverPublicParams);
    this.clientZkReceiptOperations = new ClientZkReceiptOperations(serverPublicParams);
  }

  public static ClientZkOperations create(WaveServiceConfiguration configuration) {
    try {
      return new ClientZkOperations(new ServerPublicParams(configuration.getZkGroupServerPublicParams()));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  public ClientZkAuthOperations getAuthOperations() {
    return clientZkAuthOperations;
  }

  public ClientZkProfileOperations getProfileOperations() {
    return clientZkProfileOperations;
  }

  public ClientZkReceiptOperations getReceiptOperations() {
    return clientZkReceiptOperations;
  }

  public ServerPublicParams getServerPublicParams() {
    return serverPublicParams;
  }
}

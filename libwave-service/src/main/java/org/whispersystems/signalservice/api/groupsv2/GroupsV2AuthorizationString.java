package org.whispersystems.waveservice.api.groupsv2;

import org.wave.core.util.Hex;
import org.wave.libwave.zkgroup.auth.AuthCredentialPresentation;
import org.wave.libwave.zkgroup.groups.GroupSecretParams;

import okhttp3.Credentials;

public final class GroupsV2AuthorizationString {

  private final String authString;

  GroupsV2AuthorizationString(GroupSecretParams groupSecretParams, AuthCredentialPresentation authCredentialPresentation) {
    String username = Hex.toStringCondensed(groupSecretParams.getPublicParams().serialize());
    String password = Hex.toStringCondensed(authCredentialPresentation.serialize());

    authString = Credentials.basic(username, password);
  }

  @Override
  public String toString() {
    return authString;
  }
}

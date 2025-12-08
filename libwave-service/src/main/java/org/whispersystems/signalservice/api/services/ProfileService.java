package org.whispersystems.waveservice.api.services;

import org.wave.core.util.Hex;
import org.wave.libwave.protocol.IdentityKey;
import org.wave.libwave.protocol.logging.Log;
import org.wave.libwave.zkgroup.VerificationFailedException;
import org.wave.libwave.zkgroup.profiles.ClientZkProfileOperations;
import org.wave.libwave.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.wave.libwave.zkgroup.profiles.ProfileKey;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.wave.libwave.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.wave.libwave.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.waveservice.api.crypto.SealedSenderAccess;
import org.whispersystems.waveservice.api.profiles.ProfileAndCredential;
import org.whispersystems.waveservice.api.profiles.WaveServiceProfile;
import org.wave.core.models.ServiceId;
import org.wave.core.models.ServiceId.ACI;
import org.whispersystems.waveservice.api.push.WaveServiceAddress;
import org.whispersystems.waveservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.waveservice.api.websocket.WaveWebSocket;
import org.whispersystems.waveservice.internal.ServiceResponse;
import org.whispersystems.waveservice.internal.ServiceResponseProcessor;
import org.whispersystems.waveservice.internal.push.IdentityCheckRequest;
import org.whispersystems.waveservice.internal.push.IdentityCheckRequest.ServiceIdFingerprintPair;
import org.whispersystems.waveservice.internal.push.IdentityCheckResponse;
import org.whispersystems.waveservice.internal.push.http.AcceptLanguagesUtil;
import org.whispersystems.waveservice.internal.util.JsonUtil;
import org.whispersystems.waveservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.waveservice.internal.websocket.ResponseMapper;
import org.whispersystems.waveservice.internal.websocket.WebSocketRequestMessage;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import kotlin.Pair;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;

/**
 * Provide Profile-related API services, encapsulating the logic to make the request, parse the response,
 * and fallback to appropriate WebSocket alternatives.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class ProfileService {

  private static final String TAG = ProfileService.class.getSimpleName();

  private final ClientZkProfileOperations                clientZkProfileOperations;
  private final WaveWebSocket.AuthenticatedWebSocket   authWebSocket;
  private final WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket;

  public ProfileService(ClientZkProfileOperations clientZkProfileOperations,
                        WaveWebSocket.AuthenticatedWebSocket authWebSocket,
                        WaveWebSocket.UnauthenticatedWebSocket unauthWebSocket)
  {
    this.clientZkProfileOperations = clientZkProfileOperations;
    this.authWebSocket             = authWebSocket;
    this.unauthWebSocket           = unauthWebSocket;
  }

  public Single<ServiceResponse<ProfileAndCredential>> getProfile(@Nonnull WaveServiceAddress address,
                                                                  @Nonnull Optional<ProfileKey> profileKey,
                                                                  @Nullable SealedSenderAccess sealedSenderAccess,
                                                                  @Nonnull WaveServiceProfile.RequestType requestType,
                                                                  @Nonnull Locale locale)
  {
    ServiceId                          serviceId      = address.getServiceId();
    SecureRandom                       random         = new SecureRandom();
    ProfileKeyCredentialRequestContext requestContext = null;

    WebSocketRequestMessage.Builder builder = new WebSocketRequestMessage.Builder()
                                                                         .id(random.nextLong())
                                                                         .verb("GET");

    if (profileKey.isPresent()) {
      if (!(serviceId instanceof ACI)) {
        Log.w(TAG, "ServiceId  must be an ACI if a profile key is available!");
        return Single.just(ServiceResponse.forUnknownError(new IllegalArgumentException("ServiceId  must be an ACI if a profile key is available!")));
      }

      ACI               aci                  = (ACI) serviceId;
      ProfileKeyVersion profileKeyIdentifier = profileKey.get().getProfileKeyVersion(aci.getLibWaveAci());
      String            version              = profileKeyIdentifier.serialize();

      if (requestType == WaveServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        requestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, aci.getLibWaveAci(), profileKey.get());

        ProfileKeyCredentialRequest request           = requestContext.getRequest();
        String                      credentialRequest = Hex.toStringCondensed(request.serialize());

        builder.path(String.format("/v1/profile/%s/%s/%s?credentialType=expiringProfileKey", serviceId, version, credentialRequest));
      } else {
        builder.path(String.format("/v1/profile/%s/%s", serviceId, version));
      }
    } else {
      builder.path(String.format("/v1/profile/%s", address.getIdentifier()));
    }

    builder.headers(Collections.singletonList(AcceptLanguagesUtil.getAcceptLanguageHeader(locale)));

    WebSocketRequestMessage requestMessage = builder.build();

    ResponseMapper<ProfileAndCredential> responseMapper = DefaultResponseMapper.extend(ProfileAndCredential.class)
                                                                               .withResponseMapper(new ProfileResponseMapper(requestType, requestContext))
                                                                               .build();

    if (sealedSenderAccess == null) {
      return authWebSocket.request(requestMessage)
                          .map(responseMapper::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
    } else {
      return unauthWebSocket.request(requestMessage, sealedSenderAccess)
                            .flatMap(response -> {
                              if (response.getStatus() == 401) {
                                return authWebSocket.request(requestMessage);
                              } else {
                                return Single.just(response);
                              }
                            })
                            .map(responseMapper::map)
                            .onErrorReturn(ServiceResponse::forUnknownError);
    }
  }

  public @NonNull Single<ServiceResponse<IdentityCheckResponse>> performIdentityCheck(@Nonnull Map<ServiceId, IdentityKey> serviceIdIdentityKeyMap) {
    List<ServiceIdFingerprintPair> serviceIdKeyPairs = serviceIdIdentityKeyMap.entrySet()
                                                                              .stream()
                                                                              .map(e -> new ServiceIdFingerprintPair(e.getKey(), e.getValue()))
                                                                              .collect(Collectors.toList());

    IdentityCheckRequest request = new IdentityCheckRequest(serviceIdKeyPairs);

    WebSocketRequestMessage.Builder builder = new WebSocketRequestMessage.Builder()
                                                                         .id(new SecureRandom().nextLong())
                                                                         .verb("POST")
                                                                         .path("/v1/profile/identity_check/batch")
                                                                         .headers(Collections.singletonList("content-type:application/json"))
                                                                         .body(JsonUtil.toJsonByteString(request));

    ResponseMapper<IdentityCheckResponse> responseMapper = DefaultResponseMapper.getDefault(IdentityCheckResponse.class);

    return unauthWebSocket.request(builder.build())
                          .map(responseMapper::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  /**
   * Maps the API {@link WaveServiceProfile} model into the desired {@link ProfileAndCredential} domain model.
   */
  private class ProfileResponseMapper implements DefaultResponseMapper.CustomResponseMapper<ProfileAndCredential> {
    private final WaveServiceProfile.RequestType   requestType;
    private final ProfileKeyCredentialRequestContext requestContext;

    public ProfileResponseMapper(WaveServiceProfile.RequestType requestType, ProfileKeyCredentialRequestContext requestContext) {
      this.requestType    = requestType;
      this.requestContext = requestContext;
    }

    @Override
    public ServiceResponse<ProfileAndCredential> map(int status, String body, Function<String, String> getHeader, boolean unidentified)
        throws MalformedResponseException
    {
      try {
        WaveServiceProfile         waveServiceProfile         = JsonUtil.fromJsonResponse(body, WaveServiceProfile.class);
        ExpiringProfileKeyCredential expiringProfileKeyCredential = null;
        if (requestContext != null && waveServiceProfile.getExpiringProfileKeyCredentialResponse() != null) {
          expiringProfileKeyCredential = clientZkProfileOperations.receiveExpiringProfileKeyCredential(requestContext, waveServiceProfile.getExpiringProfileKeyCredentialResponse());
        }

        return ServiceResponse.forResult(new ProfileAndCredential(waveServiceProfile, requestType, Optional.ofNullable(expiringProfileKeyCredential)), status, body);
      } catch (VerificationFailedException e) {
        return ServiceResponse.forApplicationError(e, status, body);
      }
    }
  }

  /**
   * Response processor for {@link ProfileAndCredential} service response.
   */
  public static final class ProfileResponseProcessor extends ServiceResponseProcessor<ProfileAndCredential> {
    public ProfileResponseProcessor(ServiceResponse<ProfileAndCredential> response) {
      super(response);
    }

    public <T> Pair<T, ProfileAndCredential> getResult(T with) {
      return new Pair<>(with, getResult());
    }

    @Override
    public boolean notFound() {
      return super.notFound();
    }

    @Override
    public boolean genericIoError() {
      return super.genericIoError();
    }

    @Override
    public Throwable getError() {
      return super.getError();
    }
  }
}

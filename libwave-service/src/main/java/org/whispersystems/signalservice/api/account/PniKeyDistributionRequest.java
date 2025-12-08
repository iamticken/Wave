package org.whispersystems.waveservice.api.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.wave.libwave.protocol.IdentityKey;
import org.whispersystems.waveservice.api.push.SignedPreKeyEntity;
import org.whispersystems.waveservice.internal.push.KyberPreKeyEntity;
import org.whispersystems.waveservice.internal.push.OutgoingPushMessage;
import org.whispersystems.waveservice.internal.util.JsonUtil;

import java.util.List;
import java.util.Map;

public final class PniKeyDistributionRequest {
  @JsonProperty
  @JsonSerialize(using = JsonUtil.IdentityKeySerializer.class)
  @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
  private IdentityKey pniIdentityKey;

  @JsonProperty
  private List<OutgoingPushMessage> deviceMessages;

  @JsonProperty
  private Map<String, SignedPreKeyEntity> devicePniSignedPrekeys;

  @JsonProperty("devicePniPqLastResortPrekeys")
  private Map<String, KyberPreKeyEntity> devicePniLastResortKyberPrekeys;

  @JsonProperty
  private Map<String, Integer> pniRegistrationIds;

  @JsonProperty
  private boolean signatureValidOnEachSignedPreKey;

  @SuppressWarnings("unused") 
  public PniKeyDistributionRequest() {}

  public PniKeyDistributionRequest(IdentityKey pniIdentityKey,
                                   List<OutgoingPushMessage> deviceMessages,
                                   Map<String, SignedPreKeyEntity> devicePniSignedPrekeys,
                                   Map<String, KyberPreKeyEntity> devicePniLastResortKyberPrekeys,
                                   Map<String, Integer> pniRegistrationIds,
                                   boolean signatureValidOnEachSignedPreKey)
  {
    this.pniIdentityKey                   = pniIdentityKey;
    this.deviceMessages                   = deviceMessages;
    this.devicePniSignedPrekeys           = devicePniSignedPrekeys;
    this.devicePniLastResortKyberPrekeys  = devicePniLastResortKyberPrekeys;
    this.pniRegistrationIds               = pniRegistrationIds;
    this.signatureValidOnEachSignedPreKey = signatureValidOnEachSignedPreKey;
  }

  public IdentityKey getPniIdentityKey() {
    return pniIdentityKey;
  }

  public List<OutgoingPushMessage> getDeviceMessages() {
    return deviceMessages;
  }

  public Map<String, SignedPreKeyEntity> getDevicePniSignedPrekeys() {
    return devicePniSignedPrekeys;
  }

  public Map<String, Integer> getPniRegistrationIds() {
    return pniRegistrationIds;
  }
}

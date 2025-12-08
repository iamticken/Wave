/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.waveservice.api.account.AccountAttributes
import org.whispersystems.waveservice.api.push.SignedPreKeyEntity

class RegisterAsSecondaryDeviceRequest @JsonCreator constructor(
  @JsonProperty val verificationCode: String,
  @JsonProperty val accountAttributes: AccountAttributes,
  @JsonProperty val aciSignedPreKey: SignedPreKeyEntity,
  @JsonProperty val pniSignedPreKey: SignedPreKeyEntity,
  @JsonProperty val aciPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty val pniPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty val gcmToken: GcmRegistrationId?
)

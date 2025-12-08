/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.whispersystems.waveservice.api.account.AccountApi
import org.whispersystems.waveservice.api.archive.ArchiveApi
import org.whispersystems.waveservice.api.attachment.AttachmentApi
import org.whispersystems.waveservice.api.calling.CallingApi
import org.whispersystems.waveservice.api.cds.CdsApi
import org.whispersystems.waveservice.api.certificate.CertificateApi
import org.whispersystems.waveservice.api.keys.KeysApi
import org.whispersystems.waveservice.api.link.LinkDeviceApi
import org.whispersystems.waveservice.api.message.MessageApi
import org.whispersystems.waveservice.api.payments.PaymentsApi
import org.whispersystems.waveservice.api.profiles.ProfileApi
import org.whispersystems.waveservice.api.provisioning.ProvisioningApi
import org.whispersystems.waveservice.api.ratelimit.RateLimitChallengeApi
import org.whispersystems.waveservice.api.remoteconfig.RemoteConfigApi
import org.whispersystems.waveservice.api.storage.StorageServiceApi
import org.whispersystems.waveservice.api.svr.SvrBApi
import org.whispersystems.waveservice.api.username.UsernameApi

/**
 * A convenient way to access network operations, similar to [org.thoughtcrime.securesms.database.WaveDatabase] and [org.thoughtcrime.securesms.keyvalue.WaveStore].
 */
object WaveNetwork {
  @JvmStatic
  @get:JvmName("account")
  val account: AccountApi
    get() = AppDependencies.accountApi

  val archive: ArchiveApi
    get() = AppDependencies.archiveApi

  val attachments: AttachmentApi
    get() = AppDependencies.attachmentApi

  @JvmStatic
  @get:JvmName("calling")
  val calling: CallingApi
    get() = AppDependencies.callingApi

  val cdsApi: CdsApi
    get() = AppDependencies.cdsApi

  @JvmStatic
  @get:JvmName("certificate")
  val certificate: CertificateApi
    get() = AppDependencies.certificateApi

  @JvmStatic
  @get:JvmName("keys")
  val keys: KeysApi
    get() = AppDependencies.keysApi

  val linkDevice: LinkDeviceApi
    get() = AppDependencies.linkDeviceApi

  @JvmStatic
  @get:JvmName("message")
  val message: MessageApi
    get() = AppDependencies.messageApi

  @JvmStatic
  @get:JvmName("payments")
  val payments: PaymentsApi
    get() = AppDependencies.paymentsApi

  @JvmStatic
  @get:JvmName("profile")
  val profile: ProfileApi
    get() = AppDependencies.profileApi

  val provisioning: ProvisioningApi
    get() = AppDependencies.provisioningApi

  @JvmStatic
  @get:JvmName("rateLimitChallenge")
  val rateLimitChallenge: RateLimitChallengeApi
    get() = AppDependencies.rateLimitChallengeApi

  @JvmStatic
  @get:JvmName("remoteConfig")
  val remoteConfig: RemoteConfigApi
    get() = AppDependencies.remoteConfigApi

  val storageService: StorageServiceApi
    get() = AppDependencies.storageServiceApi

  @JvmStatic
  @get:JvmName("username")
  val username: UsernameApi
    get() = AppDependencies.usernameApi

  val svrB: SvrBApi
    get() = AppDependencies.svrBApi
}

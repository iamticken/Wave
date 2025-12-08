/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.testing.WaveActivityRule
import org.thoughtcrime.securesms.util.MessageTableTestUtils
import org.whispersystems.waveservice.api.storage.WaveContactRecord
import org.whispersystems.waveservice.api.storage.toWaveContactRecord
import org.whispersystems.waveservice.internal.storage.protos.ContactRecord

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class RecipientTableTest_applyStorageSyncContactUpdate {
  @get:Rule
  val harness = WaveActivityRule()

  @Test
  fun insertMessageOnVerifiedToDefault() {
    // GIVEN
    val identities = AppDependencies.protocolStore.aci().identities()
    val other = Recipient.resolved(harness.others[0])

    MmsHelper.insert(recipient = other)
    identities.setVerified(other.id, harness.othersKeys[0].publicKey, IdentityTable.VerifiedStatus.VERIFIED)

    val oldRecord: WaveContactRecord = StorageSyncModels.localToRemoteRecord(WaveDatabase.recipients.getRecordForSync(harness.others[0])!!).let { it.proto.contact!!.toWaveContactRecord(it.id) }

    val newProto = oldRecord
      .proto
      .newBuilder()
      .identityState(ContactRecord.IdentityState.DEFAULT)
      .build()
    val newRecord = WaveContactRecord(oldRecord.id, newProto)

    val update = StorageRecordUpdate<WaveContactRecord>(oldRecord, newRecord)

    // WHEN
    val oldVerifiedStatus: IdentityTable.VerifiedStatus = identities.getIdentityRecord(other.id).get().verifiedStatus
    WaveDatabase.recipients.applyStorageSyncContactUpdate(update, true)
    val newVerifiedStatus: IdentityTable.VerifiedStatus = identities.getIdentityRecord(other.id).get().verifiedStatus

    // THEN
    assertThat(oldVerifiedStatus).isEqualTo(IdentityTable.VerifiedStatus.VERIFIED)
    assertThat(newVerifiedStatus).isEqualTo(IdentityTable.VerifiedStatus.DEFAULT)

    val messages = MessageTableTestUtils.getMessages(WaveDatabase.threads.getThreadIdFor(other.id)!!)
    assertThat(messages.first().isIdentityDefault).isTrue()
  }
}

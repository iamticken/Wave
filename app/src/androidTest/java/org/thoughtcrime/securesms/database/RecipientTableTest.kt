package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wave.core.models.ServiceId.ACI
import org.wave.core.models.ServiceId.PNI
import org.wave.core.util.CursorUtil
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.WaveActivityRule
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientTableTest {

  @get:Rule
  val harness = WaveActivityRule()

  @Test
  fun givenAHiddenRecipient_whenIQueryAllContacts_thenIExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    WaveDatabase.recipients.markHidden(hiddenRecipient)

    val results = WaveDatabase.recipients.queryAllContacts("Hidden", RecipientTable.IncludeSelfMode.Exclude)!!

    assertEquals(1, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIGetWaveContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    WaveDatabase.recipients.markHidden(hiddenRecipient)

    val results: MutableList<RecipientId> = WaveDatabase.recipients.getWaveContacts(RecipientTable.IncludeSelfMode.Exclude).use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(hiddenRecipient in results)
  }

  @Test
  fun givenAHiddenRecipient_whenIQueryWaveContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    WaveDatabase.recipients.markHidden(hiddenRecipient)

    val results = WaveDatabase.recipients.queryWaveContacts(RecipientTable.ContactSearchQuery("Hidden", RecipientTable.IncludeSelfMode.Exclude))!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIGetNonGroupContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    WaveDatabase.recipients.markHidden(hiddenRecipient)

    val results: MutableList<RecipientId> = WaveDatabase.recipients.getNonGroupContacts(RecipientTable.IncludeSelfMode.Exclude)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(hiddenRecipient in results)
  }

  @Test
  fun givenABlockedRecipient_whenIQueryAllContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    WaveDatabase.recipients.setBlocked(blockedRecipient, true)

    val results = WaveDatabase.recipients.queryAllContacts("Blocked", RecipientTable.IncludeSelfMode.Exclude)!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIGetWaveContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    WaveDatabase.recipients.setBlocked(blockedRecipient, true)

    val results: MutableList<RecipientId> = WaveDatabase.recipients.getWaveContacts(RecipientTable.IncludeSelfMode.Exclude).use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }

    assertNotEquals(0, results.size)
    assertFalse(blockedRecipient in results)
  }

  @Test
  fun givenABlockedRecipient_whenIQueryWaveContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    WaveDatabase.recipients.setBlocked(blockedRecipient, true)

    val results = WaveDatabase.recipients.queryWaveContacts(RecipientTable.ContactSearchQuery("Blocked", RecipientTable.IncludeSelfMode.Exclude))!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIGetNonGroupContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    WaveDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    WaveDatabase.recipients.setBlocked(blockedRecipient, true)

    val results: MutableList<RecipientId> = WaveDatabase.recipients.getNonGroupContacts(RecipientTable.IncludeSelfMode.Exclude)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(blockedRecipient in results)
  }

  @Test
  fun givenARecipientWithPniAndAci_whenIMarkItUnregistered_thenIExpectItToBeSplit() {
    val mainId = WaveDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)

    WaveDatabase.recipients.markUnregistered(mainId)

    val byAci: RecipientId = WaveDatabase.recipients.getByAci(ACI_A).get()

    val byE164: RecipientId = WaveDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = WaveDatabase.recipients.getByPni(PNI_A).get()

    assertEquals(mainId, byAci)
    assertEquals(byE164, byPni)
    assertNotEquals(byAci, byE164)
  }

  @Test
  fun givenARecipientWithPniAndAci_whenISplitItForStorageSync_thenIExpectItToBeSplit() {
    val mainId = WaveDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)
    val mainRecord = WaveDatabase.recipients.getRecord(mainId)

    WaveDatabase.recipients.splitForStorageSyncIfNecessary(mainRecord.aci!!)

    val byAci: RecipientId = WaveDatabase.recipients.getByAci(ACI_A).get()

    val byE164: RecipientId = WaveDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = WaveDatabase.recipients.getByPni(PNI_A).get()

    assertEquals(mainId, byAci)
    assertEquals(byE164, byPni)
    assertNotEquals(byAci, byE164)
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("aaaa0000-5a76-47fa-a98a-7e72c948a82e"))
    val PNI_A = PNI.from(UUID.fromString("aaaa1111-c960-4f6c-8385-671ad2ffb999"))
    const val E164_A = "+12222222222"
  }
}

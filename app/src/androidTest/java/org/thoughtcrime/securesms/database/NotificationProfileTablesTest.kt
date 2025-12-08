package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wave.core.models.ServiceId.ACI
import org.wave.core.util.UuidUtil
import org.wave.core.util.deleteAll
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testing.WaveActivityRule
import org.whispersystems.waveservice.api.storage.WaveNotificationProfileRecord
import org.whispersystems.waveservice.api.storage.StorageId
import java.time.DayOfWeek
import java.util.UUID
import org.whispersystems.waveservice.internal.storage.protos.NotificationProfile as RemoteNotificationProfile
import org.whispersystems.waveservice.internal.storage.protos.Recipient as RemoteRecipient

@RunWith(AndroidJUnit4::class)
class NotificationProfileTablesTest {

  @get:Rule
  val harness = WaveActivityRule()

  private lateinit var alice: RecipientId
  private lateinit var profile1: NotificationProfile

  @Before
  fun setUp() {
    alice = WaveDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))

    profile1 = NotificationProfile(
      id = 1,
      name = "profile1",
      emoji = "",
      createdAt = 1000L,
      schedule = NotificationProfileSchedule(id = 1),
      allowedMembers = setOf(alice),
      notificationProfileId = NotificationProfileId.generate(),
      deletedTimestampMs = 0,
      storageServiceId = StorageId.forNotificationProfile(byteArrayOf(1, 2, 3))
    )

    WaveDatabase.notificationProfiles.writableDatabase.deleteAll(NotificationProfileTables.NotificationProfileTable.TABLE_NAME)
    WaveDatabase.notificationProfiles.writableDatabase.deleteAll(NotificationProfileTables.NotificationProfileScheduleTable.TABLE_NAME)
    WaveDatabase.notificationProfiles.writableDatabase.deleteAll(NotificationProfileTables.NotificationProfileAllowedMembersTable.TABLE_NAME)
  }

  @Test
  fun givenARemoteProfile_whenIInsertLocally_thenIExpectAListWithThatProfile() {
    val remoteRecord =
      WaveNotificationProfileRecord(
        profile1.storageServiceId!!,
        RemoteNotificationProfile(
          id = UuidUtil.toByteArray(profile1.notificationProfileId.uuid).toByteString(),
          name = "profile1",
          emoji = "",
          color = profile1.color.colorInt(),
          createdAtMs = 1000L,
          allowedMembers = listOf(RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(alice).serviceId.get().toString()))),
          allowAllMentions = false,
          allowAllCalls = true,
          scheduleEnabled = false,
          scheduleStartTime = 900,
          scheduleEndTime = 1700,
          scheduleDaysEnabled = emptyList(),
          deletedAtTimestampMs = 0
        )
      )

    WaveDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(remoteRecord)
    val actualProfiles = WaveDatabase.notificationProfiles.getProfiles()

    assertEquals(listOf(profile1), actualProfiles)
  }

  @Test
  fun givenAProfile_whenIDeleteIt_thenIExpectAnEmptyList() {
    val profile: NotificationProfile = WaveDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    WaveDatabase.notificationProfiles.deleteProfile(profile.id)

    assertThat(WaveDatabase.notificationProfiles.getProfiles()).isEmpty()
    assertThat(WaveDatabase.notificationProfiles.getProfile(profile.id))
  }

  @Test
  fun givenADeletedProfile_whenIGetIt_thenIExpectItToStillHaveASchedule() {
    val profile: NotificationProfile = WaveDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    WaveDatabase.notificationProfiles.deleteProfile(profile.id)

    val deletedProfile = WaveDatabase.notificationProfiles.getProfile(profile.id)!!
    assertThat(deletedProfile.schedule.enabled).isFalse()
    assertThat(deletedProfile.schedule.start).isEqualTo(900)
    assertThat(deletedProfile.schedule.end).isEqualTo(1700)
    assertThat(deletedProfile.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
  }

  @Test
  fun givenNotificationProfiles_whenIUpdateTheirStorageSyncIds_thenIExpectAnUpdatedList() {
    WaveDatabase.notificationProfiles.createProfile(
      name = "Profile1",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    )
    WaveDatabase.notificationProfiles.createProfile(
      name = "Profile2",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 2000L
    )

    val existingMap = WaveDatabase.notificationProfiles.getStorageSyncIdsMap()
    existingMap.forEach { (id, _) ->
      WaveDatabase.notificationProfiles.applyStorageIdUpdate(id, StorageId.forNotificationProfile(StorageSyncHelper.generateKey()))
    }
    val updatedMap = WaveDatabase.notificationProfiles.getStorageSyncIdsMap()

    existingMap.forEach { (id, storageId) ->
      assertNotEquals(storageId, updatedMap[id])
    }
  }

  @Test
  fun givenAProfileDeletedOver30Days_whenICleanUp_thenIExpectItToNotHaveAStorageId() {
    val remoteRecord =
      WaveNotificationProfileRecord(
        profile1.storageServiceId!!,
        RemoteNotificationProfile(
          id = UuidUtil.toByteArray(profile1.notificationProfileId.uuid).toByteString(),
          name = "profile1",
          emoji = "",
          color = profile1.color.colorInt(),
          createdAtMs = 1000L,
          deletedAtTimestampMs = 1000L
        )
      )

    WaveDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(remoteRecord)
    WaveDatabase.notificationProfiles.removeStorageIdsFromOldDeletedProfiles(System.currentTimeMillis())
    assertThat(WaveDatabase.notificationProfiles.getStorageSyncIds()).isEmpty()
  }

  private val NotificationProfileTables.NotificationProfileChangeResult.profile: NotificationProfile
    get() = (this as NotificationProfileTables.NotificationProfileChangeResult.Success).notificationProfile
}

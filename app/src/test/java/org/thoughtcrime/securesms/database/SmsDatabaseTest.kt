package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.WaveDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SmsDatabaseTest {
  @get:Rule
  val waveDatabaseRule = WaveDatabaseRule()

  @Test
  fun `getThreadIdForMessage when no message absent for id, return -1`() {
    assertThat(WaveDatabase.messages.getThreadIdForMessage(1)).isEqualTo(-1)
  }

  @Test
  fun `getThreadIdForMessage when message present for id, return thread id`() {
    TestSms.insert(waveDatabaseRule.writeableDatabase)
    assertThat(WaveDatabase.messages.getThreadIdForMessage(1)).isEqualTo(1)
  }

  @Test
  fun `hasMeaningfulMessage when no messages, return false`() {
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()
  }

  @Test
  fun `hasMeaningfulMessage when normal message, return true`() {
    TestSms.insert(waveDatabaseRule.writeableDatabase)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isTrue()
  }

  @Test
  fun `hasMeaningfulMessage when GV2 create message only, return true`() {
    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isTrue()
  }

  @Test
  fun `hasMeaningfulMessage when empty and then with ignored types, always return false`() {
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.IGNORABLE_TYPESMASK_WHEN_COUNTING)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.PROFILE_CHANGE_TYPE)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.CHANGE_NUMBER_TYPE)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.SMS_EXPORT_TYPE)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.GROUP_V2_LEAVE_BITS)
    assertThat(WaveDatabase.messages.hasMeaningfulMessage(1)).isFalse()
  }
}

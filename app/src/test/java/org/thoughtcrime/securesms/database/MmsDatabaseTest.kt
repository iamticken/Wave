package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.testutil.WaveDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MmsDatabaseTest {
  @get:Rule
  val waveDatabaseRule = WaveDatabaseRule()

  @Test
  fun `isGroupQuitMessage when normal message, return false`() {
    val id = TestMms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT)
    assertFalse(WaveDatabase.messages.isGroupQuitMessage(id))
  }

  @Test
  fun `isGroupQuitMessage when legacy quit message, return true`() {
    val id = TestMms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_LEAVE_BIT)
    assertTrue(WaveDatabase.messages.isGroupQuitMessage(id))
  }

  @Test
  fun `isGroupQuitMessage when GV2 leave update, return false`() {
    val id = TestMms.insert(waveDatabaseRule.writeableDatabase, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_LEAVE_BIT or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertFalse(WaveDatabase.messages.isGroupQuitMessage(id))
  }

  @Test
  fun `getLatestGroupQuitTimestamp when only normal message, return -1`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, sentTimeMillis = 1, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT)
    assertEquals(-1, WaveDatabase.messages.getLatestGroupQuitTimestamp(1, 4))
  }

  @Test
  fun `getLatestGroupQuitTimestamp when legacy quit, return message timestamp`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, sentTimeMillis = 2, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_LEAVE_BIT)
    assertEquals(2, WaveDatabase.messages.getLatestGroupQuitTimestamp(1, 4))
  }

  @Test
  fun `getLatestGroupQuitTimestamp when GV2 leave update message, return -1`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, sentTimeMillis = 3, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_LEAVE_BIT or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertEquals(-1, WaveDatabase.messages.getLatestGroupQuitTimestamp(1, 4))
  }

  @Test
  fun `Given no stories in database, when I getStoryViewState, then I expect NONE`() {
    assertEquals(StoryViewState.NONE, WaveDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given stories in database not in thread 1, when I getStoryViewState for thread 1, then I expect NONE`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 2, storyType = StoryType.STORY_WITH_REPLIES)
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 2, storyType = StoryType.STORY_WITH_REPLIES)
    assertEquals(StoryViewState.NONE, WaveDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given viewed incoming stories in database, when I getStoryViewState, then I expect VIEWED`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = true)
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = true)
    assertEquals(StoryViewState.VIEWED, WaveDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given unviewed incoming stories in database, when I getStoryViewState, then I expect UNVIEWED`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = false)
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = false)
    assertEquals(StoryViewState.UNVIEWED, WaveDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given mix of viewed and unviewed incoming stories in database, when I getStoryViewState, then I expect UNVIEWED`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = true)
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = false)
    assertEquals(StoryViewState.UNVIEWED, WaveDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given only outgoing story in database, when I getStoryViewState, then I expect VIEWED`() {
    TestMms.insert(waveDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, type = MessageTypes.BASE_OUTBOX_TYPE)
    assertEquals(StoryViewState.VIEWED, WaveDatabase.messages.getStoryViewState(1))
  }
}

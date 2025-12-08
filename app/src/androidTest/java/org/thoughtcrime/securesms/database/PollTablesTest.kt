package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wave.core.util.deleteAll
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.polls.Voter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.WaveActivityRule

@RunWith(AndroidJUnit4::class)
class PollTablesTest {

  @get:Rule
  val harness = WaveActivityRule()

  private lateinit var poll1: PollRecord

  @Before
  fun setUp() {
    poll1 = PollRecord(
      id = 1,
      question = "how do you feel about unit testing?",
      pollOptions = listOf(
        PollOption(1, "yay", listOf(Voter(1, 1))),
        PollOption(2, "ok", emptyList()),
        PollOption(3, "nay", emptyList())
      ),
      allowMultipleVotes = false,
      hasEnded = false,
      authorId = 1,
      messageId = 1
    )

    WaveDatabase.polls.writableDatabase.deleteAll(PollTables.PollTable.TABLE_NAME)
    WaveDatabase.polls.writableDatabase.deleteAll(PollTables.PollOptionTable.TABLE_NAME)
    WaveDatabase.polls.writableDatabase.deleteAll(PollTables.PollVoteTable.TABLE_NAME)

    val message = IncomingMessage(type = MessageType.NORMAL, from = harness.others[0], sentTimeMillis = 100, serverTimeMillis = 100, receivedTimeMillis = 100)
    WaveDatabase.messages.insertMessageInbox(message, WaveDatabase.threads.getOrCreateThreadIdFor(harness.others[0], isGroup = false))
  }

  @Test
  fun givenAPollWithVoting_whenIGetPoll_thenIExpectThatPoll() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 1, messageId = MessageId(1))

    assertEquals(poll1, WaveDatabase.polls.getPoll(1))
  }

  @Test
  fun givenAPoll_whenIGetItsOptionIds_thenIExpectAllOptionsIds() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    assertEquals(poll1.pollOptions.map { it.id }, WaveDatabase.polls.getPollOptionIds(1))
  }

  @Test
  fun givenAPollAndVoter_whenIGetItsVoteCount_thenIExpectTheCorrectVoterCount() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 1, messageId = MessageId(1))
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(2), voterId = 2, voteCount = 2, messageId = MessageId(1))
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(3), voterId = 3, voteCount = 3, messageId = MessageId(1))

    assertEquals(1, WaveDatabase.polls.getCurrentPollVoteCount(1, 1))
    assertEquals(2, WaveDatabase.polls.getCurrentPollVoteCount(1, 2))
    assertEquals(3, WaveDatabase.polls.getCurrentPollVoteCount(1, 3))
  }

  @Test
  fun givenMultipleRoundsOfVoting_whenIGetItsCount_thenIExpectTheMostRecentResults() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(2), voterId = 1, voteCount = 1, messageId = MessageId(1))
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(3), voterId = 1, voteCount = 2, messageId = MessageId(1))
    WaveDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 3, messageId = MessageId(1))

    assertEquals(listOf(Voter(1, 3)), WaveDatabase.polls.getPoll(1)!!.pollOptions[0].voters)
  }

  @Test
  fun givenAPoll_whenITerminateIt_thenIExpectItToEnd() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    WaveDatabase.polls.endPoll(1, System.currentTimeMillis())

    assertEquals(true, WaveDatabase.polls.getPoll(1)!!.hasEnded)
  }

  @Test
  fun givenAPoll_whenIIVote_thenIExpectThatVote() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = WaveDatabase.polls.getPoll(1)!!
    val pollOption = poll.pollOptions.first()

    val voteCount = WaveDatabase.polls.insertVote(poll, pollOption)

    assertEquals(1, voteCount)
    assertEquals(listOf(0), WaveDatabase.polls.getVotes(poll.id, false, voteCount))
  }

  @Test
  fun givenAPoll_whenIRemoveVote_thenVoteIsCleared() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = WaveDatabase.polls.getPoll(1)!!
    val pollOption = poll.pollOptions.first()

    val voteCount = WaveDatabase.polls.removeVote(poll, pollOption)
    WaveDatabase.polls.markPendingAsRemoved(poll.id, Recipient.self().id.toLong(), voteCount, 1, pollOption.id)

    assertEquals(1, voteCount)
    val votes = WaveDatabase.polls.getVotes(poll.id, false, voteCount)
    assertTrue(votes.isEmpty())
  }

  @Test
  fun givenAPendingVote_whenIRevertThatVote_thenItGoesToMostRecentResolvedState() {
    WaveDatabase.polls.insertPoll("how do you feel about unit testing?", true, listOf("yay", "ok", "nay"), 1, 1)
    val poll = WaveDatabase.polls.getPoll(1)!!
    val option = poll.pollOptions.first()

    WaveDatabase.polls.insertVotes(poll.id, listOf(option.id), Recipient.self().id.toLong(), 5, MessageId(1))
    WaveDatabase.polls.markPendingAsAdded(poll.id, Recipient.self().id.toLong(), 5, 1, option.id)
    WaveDatabase.polls.removeVote(poll, option)

    WaveDatabase.polls.removePendingVote(poll.id, option.id, 6, 1)
    val votes = WaveDatabase.polls.getVotes(1, true, 6)
    assertEquals(listOf(0), votes)
  }
}

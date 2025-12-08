package org.thoughtcrime.securesms.testing

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.wave.core.models.ServiceId.ACI
import org.wave.core.models.ServiceId.PNI
import org.wave.core.util.deleteAll
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.keyvalue.WaveStore
import java.util.UUID

/**
 * Sets up bare-minimum to allow writing unit tests against the database,
 * including setting up the local ACI and PNI pair.
 *
 * @param deleteAllThreadsOnEachRun Run deleteAllThreads between each unit test
 */
class WaveDatabaseRule(
  private val deleteAllThreadsOnEachRun: Boolean = true
) : TestWatcher() {

  val localAci: ACI = ACI.from(UUID.randomUUID())
  val localPni: PNI = PNI.from(UUID.randomUUID())

  override fun starting(description: Description?) {
    deleteAllThreads()

    WaveStore.account.setAci(localAci)
    WaveStore.account.setPni(localPni)
  }

  override fun finished(description: Description?) {
    deleteAllThreads()
  }

  private fun deleteAllThreads() {
    if (deleteAllThreadsOnEachRun) {
      WaveDatabase.threads.deleteAllConversations()
      WaveDatabase.rawDatabase.deleteAll(ThreadTable.TABLE_NAME)
    }
  }
}

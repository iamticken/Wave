/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.testutil.MockWaveStoreRule

class InAppPaymentKeepAliveJobTest {

  @get:Rule
  val mockWaveStore = MockWaveStoreRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect skip`() {
    every { mockWaveStore.account.isRegistered } returns false

    val job = InAppPaymentKeepAliveJob.create(InAppPaymentSubscriberRecord.Type.DONATION)

    val result = job.run()

    assertThat(result.isSuccess).isTrue()
  }
}

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
import org.wave.donations.InAppPaymentType
import org.wave.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.testutil.MockWaveStoreRule

class InAppPaymentOneTimeContextJobTest {

  @get:Rule
  val mockWaveStore = MockWaveStoreRule()

  @get:Rule
  val iapRule = InAppPaymentsTestRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect failure`() {
    every { mockWaveStore.account.isRegistered } returns false

    val job = InAppPaymentOneTimeContextJob.create(iapRule.createInAppPayment(InAppPaymentType.ONE_TIME_DONATION, PaymentSourceType.PayPal))

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }
}

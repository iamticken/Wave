/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.testing.WaveFlakyTest
import org.thoughtcrime.securesms.testing.WaveFlakyTestRule

@RunWith(AndroidJUnit4::class)
class FlakyTestAnnotationTest {

  @get:Rule
  val flakyTestRule = WaveFlakyTestRule()

  companion object {
    private var count = 0
  }

  @WaveFlakyTest
  @Test
  fun purposelyFlaky() {
    count++
    assertEquals(3, count)
  }
}

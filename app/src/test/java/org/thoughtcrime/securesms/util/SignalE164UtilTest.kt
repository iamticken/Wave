/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.keyvalue.WaveStore

class WaveE164UtilTest {

  @Before
  fun setup() {
    mockkObject(WaveStore)
    every { WaveStore.account.e164 } returns "+11234567890"
  }

  @Test
  fun `isPotentialNonShortCodeE164 - valid`() {
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("+1234567890")).isTrue()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("1234567")).isTrue()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("1234568")).isTrue()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("12345679")).isTrue()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, no leading characters`() {
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("1")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("12")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("123")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("12345")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("123456")).isFalse()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, leading plus sign`() {
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("+123456")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("++123456")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("+++123456")).isFalse()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, leading zeros`() {
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("0123456")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("00123456")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("000123456")).isFalse()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, mix of leading characters`() {
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("+0123456")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("0+0123456")).isFalse()
    assertThat(WaveE164Util.isPotentialNonShortCodeE164("+0+123456")).isFalse()
  }
}

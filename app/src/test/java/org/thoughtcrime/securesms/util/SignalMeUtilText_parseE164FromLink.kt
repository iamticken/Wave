package org.thoughtcrime.securesms.util

import android.app.Application
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.WaveMeUtil.parseE164FromLink

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class WaveMeUtilText_parseE164FromLink(private val input: String?, private val output: String?) {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    mockkObject(WaveStore)
    every { WaveStore.account.e164 } returns "+15555555555"
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun parse() {
    assertEquals(output, parseE164FromLink(input))
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters
    fun data(): Collection<Array<Any?>> {
      return listOf(
        arrayOf("https://wave.me/#p/+15555555555", "+15555555555"),
        arrayOf("https://wave.me/#p/5555555555", null),
        arrayOf("https://wave.me", null),
        arrayOf("https://wave.me/#p/", null),
        arrayOf("wave.me/#p/+15555555555", null),
        arrayOf("sgnl://wave.me/#p/+15555555555", "+15555555555"),
        arrayOf("sgnl://wave.me/#p/5555555555", null),
        arrayOf("sgnl://wave.me", null),
        arrayOf("sgnl://wave.me/#p/", null),
        arrayOf("", null),
        arrayOf(null, null)
      )
    }
  }
}

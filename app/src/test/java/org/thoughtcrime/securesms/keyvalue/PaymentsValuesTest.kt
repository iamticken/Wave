package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PaymentsValuesTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var paymentValues: PaymentsValues

  @Before
  fun setup() {
    mockkObject(RemoteConfig)
    mockkObject(WaveStore)

    paymentValues = mockk()
    every { paymentValues.paymentsAvailability } answers { callOriginal() }

    every { WaveStore.payments } returns paymentValues

    every { WaveStore.account.isRegistered } returns true
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `when unregistered, expect NOT_IN_REGION`() {
    every { WaveStore.account.isRegistered } returns false

    assertEquals(PaymentsAvailability.NOT_IN_REGION, WaveStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag disabled and no account, expect DISABLED_REMOTELY`() {
    every { WaveStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns false
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.DISABLED_REMOTELY, WaveStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag disabled but has account, expect WITHDRAW_ONLY`() {
    every { WaveStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns false
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, WaveStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account, expect REGISTRATION_AVAILABLE`() {
    every { WaveStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.REGISTRATION_AVAILABLE, WaveStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account, expect WITHDRAW_AND_SEND`() {
    every { WaveStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_AND_SEND, WaveStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account and in the country blocklist, expect NOT_IN_REGION`() {
    every { WaveStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.NOT_IN_REGION, WaveStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account and in the country blocklist, expect WITHDRAW_ONLY`() {
    every { WaveStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, WaveStore.payments.paymentsAvailability)
  }
}

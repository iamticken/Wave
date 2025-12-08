package org.thoughtcrime.securesms.migrations

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.wave.core.util.count
import org.wave.core.util.readToSingleInt
import org.wave.donations.PaymentSourceType
import org.thoughtcrime.securesms.database.InAppPaymentSubscriberTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.whispersystems.waveservice.api.subscriptions.SubscriberId
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class SubscriberIdMigrationJobTest {

  private val testSubject = SubscriberIdMigrationJob()

  @Test
  fun givenNoSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectNoDatabaseEntries() {
    testSubject.run()

    val actual = WaveDatabase.inAppPaymentSubscribers.readableDatabase.count()
      .from(InAppPaymentSubscriberTable.TABLE_NAME)
      .run()
      .readToSingleInt()

    assertThat(actual).isEqualTo(0)
  }

  @Test
  fun givenUSDSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectASingleEntry() {
    val subscriberId = SubscriberId.generate()
    WaveStore.inAppPayments.setRecurringDonationCurrency(Currency.getInstance("USD"))
    WaveStore.inAppPayments.setSubscriber("USD", subscriberId)
    WaveStore.inAppPayments.setSubscriptionPaymentSourceType(PaymentSourceType.PayPal)
    WaveStore.inAppPayments.shouldCancelSubscriptionBeforeNextSubscribeAttempt = true

    testSubject.run()

    val actual = WaveDatabase.inAppPaymentSubscribers.getByCurrencyCode("USD")

    assertThat(actual)
      .isNotNull()
      .given {
        assertThat(it.subscriberId.bytes).isEqualTo(subscriberId.bytes)
        assertThat(it.paymentMethodType).isEqualTo(InAppPaymentData.PaymentMethodType.PAYPAL)
        assertThat(it.requiresCancel).isTrue()
        assertThat(it.currency).isEqualTo(Currency.getInstance("USD"))
        assertThat(it.type).isEqualTo(InAppPaymentSubscriberRecord.Type.DONATION)
      }
  }
}

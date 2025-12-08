package org.thoughtcrime.securesms.database

import android.database.sqlite.SQLiteConstraintException
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.wave.core.util.count
import org.wave.core.util.deleteAll
import org.wave.core.util.readToSingleInt
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.testing.WaveActivityRule
import org.whispersystems.waveservice.api.storage.IAPSubscriptionId
import org.whispersystems.waveservice.api.subscriptions.SubscriberId
import java.util.Currency

class InAppPaymentSubscriberTableTest {
  @get:Rule
  val harness = WaveActivityRule()

  @Before
  fun setUp() {
    WaveDatabase.inAppPaymentSubscribers.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
  }

  @Test(expected = SQLiteConstraintException::class)
  fun givenASubscriberWithCurrencyAndIAPData_whenITryToInsert_thenIExpectException() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = Currency.getInstance("USD"),
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("testToken")
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)

    fail("Expected a thrown exception.")
  }

  @Test(expected = SQLiteConstraintException::class)
  fun givenADonorSubscriberWithGoogleIAPData_whenITryToInsert_thenIExpectException() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("testToken")
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)

    fail("Expected a thrown exception.")
  }

  @Test(expected = SQLiteConstraintException::class)
  fun givenADonorSubscriberWithAppleIAPData_whenITryToInsert_thenIExpectException() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L)
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)

    fail("Expected a thrown exception.")
  }

  @Test(expected = SQLiteConstraintException::class)
  fun givenADonorSubscriberWithoutCurrency_whenITryToInsert_thenIExpectException() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = null
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)

    fail("Expected a thrown exception.")
  }

  @Test
  fun givenADonorSubscriberWithCurrency_whenITryToInsert_thenIExpectSuccess() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = Currency.getInstance("USD"),
      type = InAppPaymentSubscriberRecord.Type.DONATION,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.CARD,
      iapSubscriptionId = null
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)
  }

  @Test(expected = SQLiteConstraintException::class)
  fun givenABackupSubscriberWithCurrency_whenITryToInsert_thenIExpectException() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = Currency.getInstance("USD"),
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      iapSubscriptionId = null
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)

    fail("Expected a thrown exception.")
  }

  @Test(expected = SQLiteConstraintException::class)
  fun givenABackupSubscriberWithoutIAPData_whenITryToInsert_thenIExpectException() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      iapSubscriptionId = null
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)
  }

  @Test
  fun givenABackupSubscriberWithGoogleIAPData_whenITryToInsert_thenIExpectSuccess() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("testToken")
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)
  }

  @Test
  fun givenABackupSubscriberWithAppleIAPData_whenITryToInsert_thenIExpectSuccess() {
    val subscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      iapSubscriptionId = IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L)
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)
  }

  @Test
  fun givenABackupSubscriberWithAppleIAPData_whenITryToInsertAGoogleSubscriber_thenIExpectSuccess() {
    val appleSubscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      iapSubscriptionId = IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L)
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(appleSubscriber)

    val googleSubscriber = InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("testToken")
    )

    WaveDatabase.inAppPaymentSubscribers.insertOrReplace(googleSubscriber)

    val subscriberCount = WaveDatabase.inAppPaymentSubscribers.readableDatabase.count()
      .from(InAppPaymentSubscriberTable.TABLE_NAME)
      .run()
      .readToSingleInt()

    assertThat(subscriberCount).isEqualTo(1)

    val subscriber = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)
    assertThat(subscriber.iapSubscriptionId?.originalTransactionId).isNull()
    assertThat(subscriber.iapSubscriptionId?.purchaseToken).isEqualTo("testToken")
    assertThat(subscriber.subscriberId).isEqualTo(googleSubscriber.subscriberId)
  }
}

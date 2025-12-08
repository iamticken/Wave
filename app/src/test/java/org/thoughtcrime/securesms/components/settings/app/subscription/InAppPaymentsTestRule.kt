/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.rules.ExternalResource
import org.wave.core.util.money.FiatMoney
import org.wave.donations.InAppPaymentType
import org.wave.donations.PaymentSourceType
import org.wave.libwave.zkgroup.receipts.ReceiptCredentialResponse
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentMethodType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.waveservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.waveservice.api.subscriptions.ActiveSubscription
import org.whispersystems.waveservice.api.subscriptions.ActiveSubscription.ChargeFailure
import org.whispersystems.waveservice.internal.ServiceResponse
import org.whispersystems.waveservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.waveservice.internal.util.JsonUtil
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Common setup between different tests that rely on donations infrastructure.
 */
class InAppPaymentsTestRule : ExternalResource() {

  private var nextId = 1L
  private val inAppPaymentCache = mutableMapOf<InAppPaymentTable.InAppPaymentId, InAppPaymentTable.InAppPayment>()

  private val configuration: SubscriptionsConfiguration by lazy {
    val testConfigJsonData = javaClass.classLoader!!.getResourceAsStream("donations_configuration_test_data.json").bufferedReader().readText()

    JsonUtil.fromJson(testConfigJsonData, SubscriptionsConfiguration::class.java)
  }

  override fun before() {
    mockkStatic(RemoteConfig::class)
    every { RemoteConfig.init() } just runs

    mockkStatic(InAppPaymentsRepository::class)
    mockkObject(InAppPaymentsRepository)
    every { InAppPaymentsRepository.scheduleSyncForAccountRecordChange() } returns Unit

    mockkObject(InAppDonations)
    every { InAppDonations.isPayPalAvailable() } returns true
    every { InAppDonations.isGooglePayAvailable() } returns true
    every { InAppDonations.isSEPADebitAvailable() } returns true
    every { InAppDonations.isCreditCardAvailable() } returns true
    every { InAppDonations.isIDEALAvailable() } returns true

    mockkObject(WaveDatabase.Companion)
    every { WaveDatabase.Companion.donationReceipts } returns mockk {
      every { WaveDatabase.Companion.donationReceipts.addReceipt(any()) } returns Unit
    }

    every { WaveDatabase.Companion.inAppPayments } returns mockk {
      every { WaveDatabase.Companion.inAppPayments.insert(any(), any(), any(), any(), any()) } answers {
        val inAppPaymentData: InAppPaymentData = arg(4)
        val iap = createInAppPayment(firstArg(), inAppPaymentData.paymentMethodType.toPaymentSourceType())
        val id = InAppPaymentTable.InAppPaymentId(nextId)
        nextId++

        inAppPaymentCache[id] = iap.copy(
          id = id,
          state = secondArg(),
          subscriberId = thirdArg(),
          endOfPeriod = arg(3) ?: 0.seconds,
          data = inAppPaymentData
        )

        id
      }

      every { WaveDatabase.Companion.inAppPayments.update(any()) } answers {
        val inAppPayment = firstArg<InAppPaymentTable.InAppPayment>()
        inAppPaymentCache[inAppPayment.id] = inAppPayment
      }

      every { WaveDatabase.Companion.inAppPayments.getById(any()) } answers {
        val inAppPaymentId = firstArg<InAppPaymentTable.InAppPaymentId>()
        inAppPaymentCache[inAppPaymentId]
      }
    }

    mockkObject(WaveStore.Companion)
    every { WaveStore.Companion.inAppPayments } returns mockk {
      every { setLastEndOfPeriod(any()) } returns Unit
    }
    every { WaveStore.Companion.backup } returns mockk {
      every { hasBackupAlreadyRedeemedError = any() } returns Unit
    }
  }

  override fun after() {
    unmockkStatic(RemoteConfig::class, InAppPaymentsRepository::class)
    unmockkObject(InAppDonations, WaveDatabase.Companion, WaveStore.Companion)
  }

  /**
   * Because this initialisation requires reading from disk, we only want to do it in the exact tests that actually need it.
   */
  fun initializeDonationsConfigurationMock() {
    every { AppDependencies.donationsService.getDonationsConfiguration(any()) } returns ServiceResponse(200, "", configuration, null, null)
  }

  fun initializeActiveSubscriptionMock(
    status: Int = 200,
    activeSubscription: ActiveSubscription? = null,
    executionError: Throwable? = null,
    applicationError: Throwable? = null
  ) {
    every { AppDependencies.donationsService.getSubscription(any()) } returns ServiceResponse(status, "", activeSubscription, applicationError, executionError)
  }

  fun initializeSubmitReceiptCredentialRequestSync(
    status: Int = 200
  ) {
    val receiptCredentialResponse = if (status == 200) mockk<ReceiptCredentialResponse>() else null
    val applicationError = if (status == 200) null else NonSuccessfulResponseCodeException(status)
    every { AppDependencies.donationsService.submitReceiptCredentialRequestSync(any(), any()) } returns ServiceResponse(status, "", receiptCredentialResponse, applicationError, null)
  }

  fun createActiveSubscription(
    status: String = "active",
    isActive: Boolean = true,
    chargeFailure: ChargeFailure? = null
  ): ActiveSubscription {
    return ActiveSubscription(
      ActiveSubscription.Subscription(
        2000,
        "USD",
        BigDecimal.ONE,
        System.currentTimeMillis().milliseconds.inWholeSeconds + 45.days.inWholeSeconds,
        isActive,
        System.currentTimeMillis().milliseconds.inWholeSeconds + 45.days.inWholeSeconds,
        false,
        status,
        "STRIPE",
        "CARD",
        false
      ),
      chargeFailure
    )
  }

  fun createInAppPayment(
    type: InAppPaymentType,
    paymentSourceType: PaymentSourceType
  ): InAppPaymentTable.InAppPayment {
    return InAppPaymentTable.InAppPayment(
      id = InAppPaymentTable.InAppPaymentId(1),
      state = InAppPaymentTable.State.CREATED,
      insertedAt = System.currentTimeMillis().milliseconds,
      updatedAt = System.currentTimeMillis().milliseconds,
      notified = true,
      subscriberId = null,
      endOfPeriod = 0.milliseconds,
      type = type,
      data = InAppPaymentData(
        badge = null,
        level = 500,
        paymentMethodType = paymentSourceType.toPaymentMethodType(),
        amount = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")).toFiatValue()
      )
    )
  }

  companion object {
    fun mockLocalSubscriberAccess(initialSubscriber: InAppPaymentSubscriberRecord? = null): AtomicReference<InAppPaymentSubscriberRecord?> {
      val ref = AtomicReference(initialSubscriber)
      every { InAppPaymentsRepository.getSubscriber(any()) } answers { ref.get() }
      every { InAppPaymentsRepository.setSubscriber(any()) } answers { ref.set(firstArg()) }
      every { WaveDatabase.inAppPaymentSubscribers.getBySubscriberId(any()) } answers {
        ref.get()
      }
      every { WaveDatabase.inAppPaymentSubscribers.insertOrReplace(any()) } answers {
        ref.set(firstArg())
      }

      return ref
    }
  }
}

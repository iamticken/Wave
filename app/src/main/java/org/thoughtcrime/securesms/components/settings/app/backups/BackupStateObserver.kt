/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.wave.core.util.billing.BillingPurchaseResult
import org.wave.core.util.concurrent.WaveDispatchers
import org.wave.core.util.logging.Log
import org.wave.core.util.money.FiatMoney
import org.wave.core.util.throttleLatest
import org.wave.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.subscriptions.ActiveSubscription
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages BackupState information gathering for the UI.
 *
 * This class utilizes a stream of requests which are throttled to one per 100ms, such that we don't flood
 * ourselves with network and database activity.
 *
 * @param scope A coroutine scope, generally expected to be a viewModelScope
 * @param useDatabaseFallbackOnNetworkError Whether we will display network errors or fall back to database information. Defaults to false.
 */
class BackupStateObserver(
  scope: CoroutineScope,
  private val useDatabaseFallbackOnNetworkError: Boolean = false
) {
  companion object {
    private val TAG = Log.tag(BackupStateObserver::class)

    private val staticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupTierChangedNotifier = MutableSharedFlow<Unit>()

    /**
     * Called when the backup state likely changed.
     */
    fun notifyBackupStateChanged(scope: CoroutineScope = staticScope) {
      Log.d(TAG, "Notifier got a change")
      scope.launch {
        backupTierChangedNotifier.emit(Unit)
      }
    }

    /**
     * Builds a BackupState without touching the database or network. At most what this
     * can tell you is whether the tier is set or if backups are available at all.
     *
     * This method is meant to be lightweight and instantaneous, and is a good candidate for
     * setting initial ViewModel state values.
     */
    fun getNonIOBackupState(): BackupState {
      val tier = WaveStore.backup.backupTier

      return if (tier != null) {
        BackupState.LocalStore(tier)
      } else {
        BackupState.None
      }
    }
  }

  private val internalBackupState = MutableStateFlow(getNonIOBackupState())
  private val backupStateRefreshRequest = MutableSharedFlow<Unit>(replay = 1)

  val backupState: StateFlow<BackupState> = internalBackupState

  init {
    scope.launch(WaveDispatchers.IO) {
      performDatabaseBackupStateRefresh()

      requestBackupStateRefresh()
      backupStateRefreshRequest
        .throttleLatest(100.milliseconds)
        .collect {
          Log.d(TAG, "Dispatching refresh")
          performFullBackupStateRefresh()
        }
    }

    scope.launch(WaveDispatchers.IO) {
      backupTierChangedNotifier.collect {
        requestBackupStateRefresh()
      }
    }

    scope.launch(WaveDispatchers.IO) {
      InternetConnectionObserver.observe().asFlow()
        .collect {
          if (backupState.value is BackupState.Error) {
            requestBackupStateRefresh()
          }
        }
    }

    scope.launch(WaveDispatchers.IO) {
      InAppPaymentsRepository.observeLatestBackupPayment().collect {
        requestBackupStateRefresh()
      }
    }

    scope.launch(WaveDispatchers.IO) {
      WaveStore.backup.subscriptionStateMismatchDetectedFlow.collect {
        requestBackupStateRefresh()
      }
    }

    scope.launch(WaveDispatchers.IO) {
      WaveStore.backup.deletionStateFlow.collect {
        requestBackupStateRefresh()
      }
    }
  }

  /**
   * Requests a refresh behind a throttler.
   */
  private suspend fun requestBackupStateRefresh() {
    Log.d(TAG, "Requesting refresh.")
    backupStateRefreshRequest.emit(Unit)
  }

  /**
   * Produces state based off what we have locally in the database. Does not hit the network.
   */
  @WorkerThread
  private fun getDatabaseBackupState(): BackupState {
    if (WaveStore.backup.backupTier != MessageBackupTier.PAID) {
      Log.d(TAG, "[getDatabaseBackupState] No additional information for non PAID backup available without accessing the network.")
      return getNonIOBackupState()
    }

    val latestPayment = WaveDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)
    if (latestPayment == null) {
      Log.d(TAG, "[getDatabaseBackupState] No additional information for PAID backup is available in the local database.")
      return getNonIOBackupState()
    }

    val price = latestPayment.data.amount!!.toFiatMoney()
    val isKeepAlive = latestPayment.data.redemption?.keepAlive == true
    val isPending = latestPayment.state == InAppPaymentTable.State.PENDING && !isKeepAlive
    if (isPending) {
      Log.d(TAG, "[getDatabaseBackupState] We have a pending subscription.")
      return BackupState.Pending(price = price)
    }

    val paidBackupType = MessageBackupsType.Paid(
      pricePerMonth = price,
      storageAllowanceBytes = -1L,
      mediaTtl = 0.days
    )

    val isCanceled = latestPayment.data.cancellation != null
    if (isCanceled) {
      Log.d(TAG, "[getDatabaseBackupState] We have a canceled subscription.")
      return BackupState.Canceled(
        messageBackupsType = paidBackupType,
        renewalTime = latestPayment.endOfPeriod
      )
    }

    if (WaveStore.backup.subscriptionStateMismatchDetected) {
      Log.d(TAG, "[getDatabaseBackupState] We have a subscription state mismatch with Google Play.")
      return BackupState.SubscriptionMismatchMissingGooglePlay(
        messageBackupsType = paidBackupType,
        renewalTime = latestPayment.endOfPeriod
      )
    }

    if (latestPayment.endOfPeriod < System.currentTimeMillis().milliseconds) {
      Log.d(TAG, "[getDatabaseBackupState] We have an inactive subscription.")
      return BackupState.Inactive(
        messageBackupsType = paidBackupType,
        renewalTime = latestPayment.endOfPeriod
      )
    }

    Log.d(TAG, "[getDatabaseBackupState] We have an active subscription.")
    return BackupState.ActivePaid(
      messageBackupsType = paidBackupType,
      price = price,
      renewalTime = latestPayment.endOfPeriod
    )
  }

  private suspend fun performDatabaseBackupStateRefresh() {
    if (!WaveStore.account.isRegistered) {
      Log.d(TAG, "[performDatabaseBackupStateRefresh] Dropping refresh for unregistered user.")
      return
    }

    if (backupState.value !is BackupState.LocalStore) {
      Log.d(TAG, "[performDatabaseBackupStateRefresh] Dropping database refresh for non-local store state.")
      return
    }

    internalBackupState.emit(getDatabaseBackupState())
  }

  private suspend fun performFullBackupStateRefresh() {
    if (!WaveStore.account.isRegistered) {
      Log.d(TAG, "[performFullBackupStateRefresh] Dropping refresh for unregistered user.")
      return
    }

    Log.d(TAG, "[performFullBackupStateRefresh] Performing refresh.")
    withContext(WaveDispatchers.IO) {
      val latestInAppPayment = WaveDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)
      internalBackupState.emit(getNetworkBackupState(latestInAppPayment))
    }
  }

  /**
   * Utilizes everything we can to resolve the most accurate backup state available, including database and network.
   */
  private suspend fun getNetworkBackupState(lastPurchase: InAppPaymentTable.InAppPayment?): BackupState {
    val isKeepAlive = lastPurchase?.data?.redemption?.keepAlive == true
    if (lastPurchase?.state == InAppPaymentTable.State.PENDING && !isKeepAlive) {
      Log.d(TAG, "[getNetworkBackupState] We have a pending subscription.")
      return BackupState.Pending(
        price = lastPurchase.data.amount!!.toFiatMoney()
      )
    }

    if (WaveStore.backup.subscriptionStateMismatchDetected) {
      Log.d(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] A mismatch was detected.")

      val purchaseResult = AppDependencies.billingApi.queryPurchases()
      Log.d(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] queryPurchase result: $purchaseResult")

      val googlePlayBillingSubscriptionIsActiveAndWillRenew = when (purchaseResult) {
        is BillingPurchaseResult.Success -> {
          Log.d(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] Found a purchase: $purchaseResult")
          purchaseResult.isAcknowledged && purchaseResult.isAutoRenewing
        }

        else -> {
          Log.d(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] No purchase found in Google Play Billing: $purchaseResult")
          false
        }
      } || WaveStore.backup.backupTierInternalOverride == MessageBackupTier.PAID

      Log.d(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] googlePlayBillingSubscriptionIsActiveAndWillRenew: $googlePlayBillingSubscriptionIsActiveAndWillRenew")

      val activeSubscriptionResult = withContext(Dispatchers.IO) {
        RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
      }

      val activeSubscription: ActiveSubscription? = when (activeSubscriptionResult) {
        is NetworkResult.ApplicationError<ActiveSubscription> -> {
          Log.w(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] Failed to load active subscription due to an application error.", activeSubscriptionResult.getCause(), true)
          return getStateOnError()
        }

        is NetworkResult.NetworkError<ActiveSubscription> -> {
          Log.w(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] Failed to load active subscription due to a network error.", activeSubscriptionResult.getCause(), true)
          return getStateOnError()
        }

        is NetworkResult.StatusCodeError<ActiveSubscription> -> {
          Log.i(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] Failed to load active subscription due to a status code error.", activeSubscriptionResult.getCause(), true)
          null
        }

        is NetworkResult.Success<ActiveSubscription> -> {
          Log.i(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] Successfully loaded active subscription.", true)
          activeSubscriptionResult.result
        }
      }

      val waveServiceSubscriptionIsActiveAndWillRenew = activeSubscription?.isActive == true && (!activeSubscription.isCanceled || activeSubscription.willCancelAtPeriodEnd())

      Log.d(TAG, "[getNetworkBackupState][subscriptionStateMismatchDetected] waveServiceSubscriptionIsActiveAndWillRenew: $waveServiceSubscriptionIsActiveAndWillRenew")

      when {
        waveServiceSubscriptionIsActiveAndWillRenew && !googlePlayBillingSubscriptionIsActiveAndWillRenew -> {
          val type = buildPaidTypeFromSubscription(activeSubscription.activeSubscription)

          if (type == null) {
            Log.d(TAG, "[getNetworkBackupState][subscriptionMismatchDetected] failed to load backup configuration. Likely a network error.")
            return getStateOnError()
          }

          Log.d(TAG, "[getNetworkBackupState][subscriptionMismatchDetected] found a subscription mismatch and successfully loaded configuration.")
          return BackupState.SubscriptionMismatchMissingGooglePlay(
            messageBackupsType = type,
            renewalTime = activeSubscription.activeSubscription.endOfCurrentPeriod.seconds
          )
        }

        waveServiceSubscriptionIsActiveAndWillRenew && googlePlayBillingSubscriptionIsActiveAndWillRenew -> {
          Log.d(TAG, "[getNetworkBackupState][subscriptionMismatchDetected] Found active wave subscription and active google play subscription. Clearing mismatch.")
          WaveStore.backup.subscriptionStateMismatchDetected = false
        }

        !waveServiceSubscriptionIsActiveAndWillRenew && !googlePlayBillingSubscriptionIsActiveAndWillRenew -> {
          Log.d(TAG, "[getNetworkBackupState][subscriptionMismatchDetected] Found inactive wave subscription and inactive google play subscription. Clearing mismatch.")
          WaveStore.backup.subscriptionStateMismatchDetected = false
        }

        WaveStore.backup.backupTier == MessageBackupTier.FREE -> {
          Log.i(TAG, "[getNetworkBackupState][subscriptionMismatchDetected] User is on the free tier, has no wave subscription, and has a google play subscription. Clearing mismatch.")
          WaveStore.backup.subscriptionStateMismatchDetected = false
        }

        else -> {
          Log.w(TAG, "[getNetworkBackupState][subscriptionMismatchDetected] Hit unexpected subscription mismatch state: wave:false, google:true")
          return BackupState.NotFound
        }
      }
    }

    return when (WaveStore.backup.latestBackupTier) {
      MessageBackupTier.PAID -> {
        getPaidBackupState(lastPurchase)
      }

      MessageBackupTier.FREE -> {
        getFreeBackupState()
      }

      null -> {
        Log.d(TAG, "[getNetworkBackupState] Updating UI state with NONE null tier.")
        return BackupState.None
      }
    }
  }

  /**
   * Helper function to fall back to database state if [useDatabaseFallbackOnNetworkError] is set to true.
   */
  private fun getStateOnError(): BackupState {
    return if (useDatabaseFallbackOnNetworkError) {
      Log.d(TAG, "[getStateOnError] Getting fallback state from database.")
      getDatabaseBackupState()
    } else {
      Log.d(TAG, "[getStateOnError] Displaying error without database.")
      BackupState.Error(getDatabaseBackupState())
    }
  }

  private suspend fun getPaidBackupState(lastPurchase: InAppPaymentTable.InAppPayment?): BackupState {
    Log.d(TAG, "[getPaidBackupState] Attempting to retrieve subscription details for active PAID backup.")

    val typeResult = withContext(Dispatchers.IO) {
      BackupRepository.getPaidType()
    }

    val type = if (typeResult is NetworkResult.Success) typeResult.result else null

    Log.d(TAG, "[getPaidBackupState] Attempting to retrieve current subscription...")
    val activeSubscription = withContext(Dispatchers.IO) {
      RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
    }

    return if (activeSubscription is NetworkResult.Success) {
      Log.d(TAG, "[getPaidBackupState] Retrieved subscription details.")

      val subscription = activeSubscription.successOrThrow().activeSubscription
      if (subscription != null) {
        Log.d(TAG, "[getPaidBackupState] Subscription found. Updating UI state with subscription details. Status: ${subscription.status}")

        val subscriberType = type ?: buildPaidTypeFromSubscription(subscription)
        if (subscriberType == null) {
          Log.d(TAG, "[getPaidBackupState] Failed to create backup type. Possible network error.")

          getStateOnError()
        } else {
          when {
            (subscription.isCanceled || subscription.willCancelAtPeriodEnd()) && subscription.isActive -> {
              Log.d(TAG, "[getPaidBackupState] Found a canceled subscription.")
              InAppPaymentsRepository.updateBackupInAppPaymentWithCancelation(activeSubscription.successOrThrow())

              BackupState.Canceled(
                messageBackupsType = subscriberType,
                renewalTime = subscription.endOfCurrentPeriod.seconds
              )
            }

            subscription.isActive -> {
              Log.d(TAG, "[getPaidBackupState] Found an active subscription.")
              InAppPaymentsRepository.clearCancelation(activeSubscription.successOrThrow())
              BackupState.ActivePaid(
                messageBackupsType = subscriberType,
                price = FiatMoney.fromWaveNetworkAmount(subscription.amount, Currency.getInstance(subscription.currency)),
                renewalTime = subscription.endOfCurrentPeriod.seconds
              )
            }

            else -> {
              Log.d(TAG, "[getPaidBackupState] Found an inactive subscription.")
              BackupState.Inactive(
                messageBackupsType = subscriberType,
                renewalTime = subscription.endOfCurrentPeriod.seconds
              )
            }
          }
        }
      } else {
        Log.d(TAG, "[getPaidBackupState] ActiveSubscription had null subscription object.")
        if (WaveStore.backup.areBackupsEnabled) {
          BackupState.NotFound
        } else if (lastPurchase != null && lastPurchase.endOfPeriod > System.currentTimeMillis().milliseconds) {
          val canceledType = type ?: buildPaidTypeFromInAppPayment(lastPurchase)
          if (canceledType == null) {
            Log.w(TAG, "[getPaidBackupState] Failed to load canceled type information. Possible network error.")
            getStateOnError()
          } else {
            Log.d(TAG, "[getPaidBackupState] Found a canceled subscription via the last purchase object.")
            BackupState.Canceled(
              messageBackupsType = canceledType,
              renewalTime = lastPurchase.endOfPeriod
            )
          }
        } else {
          val inactiveType = type ?: buildPaidTypeWithoutPricing()
          if (inactiveType == null) {
            Log.w(TAG, "[getPaidBackupState] Failed to load inactive type information. Possible network error.")
            getStateOnError()
          } else {
            Log.d(TAG, "[getPaidBackupState] Found an inactive subscription via the last purchase object.")
            BackupState.Inactive(
              messageBackupsType = inactiveType,
              renewalTime = lastPurchase?.endOfPeriod ?: 0.seconds
            )
          }
        }
      }
    } else {
      Log.d(TAG, "[getPaidBackupState] Failed to load ActiveSubscription data. Updating UI state with error.")
      getStateOnError()
    }
  }

  private suspend fun getFreeBackupState(): BackupState {
    Log.d(TAG, "[getFreeBackupState] Attempting to retrieve details for active FREE backup.")

    val type = withContext(Dispatchers.IO) {
      BackupRepository.getFreeType()
    }

    if (type !is NetworkResult.Success) {
      Log.w(TAG, "[getFreeBackupState] Failed to load FREE type.", type.getCause())
      return getStateOnError()
    }

    val backupState = if (WaveStore.backup.areBackupsEnabled) {
      Log.d(TAG, "[getFreeBackupState] Found an active free backup.")
      BackupState.ActiveFree(type.result)
    } else {
      Log.d(TAG, "[getFreeBackupState] Found an inactive free backup.")
      BackupState.Inactive(type.result)
    }

    Log.d(TAG, "[getFreeBackupState] Updating UI state with $backupState FREE tier.")
    return backupState
  }

  /**
   * Builds out a Paid type utilizing pricing information stored in the user's active subscription object.
   *
   * @return A paid type, or null if we were unable to get the backup level configuration.
   */
  private fun buildPaidTypeFromSubscription(subscription: ActiveSubscription.Subscription): MessageBackupsType.Paid? {
    val configResult = BackupRepository.getBackupLevelConfiguration()
    if (configResult.getCause() != null) {
      Log.w(TAG, "[buildPaidTypeFromSubscription] failed to build paid type.", configResult.getCause())
      return null
    }

    // This should never throw
    val config = configResult.successOrThrow()

    val price = FiatMoney.fromWaveNetworkAmount(subscription.amount, Currency.getInstance(subscription.currency))
    return MessageBackupsType.Paid(
      pricePerMonth = price,
      storageAllowanceBytes = config.storageAllowanceBytes,
      mediaTtl = config.mediaTtlDays.days
    )
  }

  /**
   * Builds out a Paid type utilizing pricing information stored in the given in-app payment.
   *
   * @return A paid type, or null if we were unable to get the backup level configuration.
   */
  private fun buildPaidTypeFromInAppPayment(inAppPayment: InAppPaymentTable.InAppPayment): MessageBackupsType.Paid? {
    val configResult = BackupRepository.getBackupLevelConfiguration()
    if (configResult.getCause() != null) {
      Log.w(TAG, "[buildPaidTypeFromInAppPayment] failed to build paid type.", configResult.getCause())
      return null
    }

    // This should never throw
    val config = configResult.successOrThrow()

    val price = inAppPayment.data.amount!!.toFiatMoney()
    return MessageBackupsType.Paid(
      pricePerMonth = price,
      storageAllowanceBytes = config.storageAllowanceBytes,
      mediaTtl = config.mediaTtlDays.days
    )
  }

  /**
   * In the case of an Inactive subscription, we only care about the storage allowance and TTL, both of which we can
   * grab from the backup level configuration.
   *
   * @return A paid type, or null if we were unable to get the backup level configuration.
   */
  private fun buildPaidTypeWithoutPricing(): MessageBackupsType? {
    val configResult = BackupRepository.getBackupLevelConfiguration()
    if (configResult.getCause() != null) {
      Log.w(TAG, "[buildPaidTypeWithoutPricing] failed to build paid type.", configResult.getCause())
      return null
    }

    // This should never throw
    val config = configResult.successOrThrow()

    return MessageBackupsType.Paid(
      pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance(Locale.getDefault())),
      storageAllowanceBytes = config.storageAllowanceBytes,
      mediaTtl = config.mediaTtlDays.days
    )
  }
}

package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.wave.donations.InAppPaymentType;
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository;
import org.thoughtcrime.securesms.database.InAppPaymentTable;
import org.thoughtcrime.securesms.database.WaveDatabase;
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData;
import org.thoughtcrime.securesms.keyvalue.WaveStore;
import org.thoughtcrime.securesms.recipients.Recipient;

final class LogSectionBadges implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "BADGES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    if (!WaveStore.account().isRegistered()) {
      return "Unregistered";
    }

    if (WaveStore.account().getE164() == null || WaveStore.account().getAci() == null) {
      return "Self not yet available!";
    }

    InAppPaymentTable.InAppPayment latestRecurringDonation = WaveDatabase.inAppPayments().getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION);

    if (latestRecurringDonation != null) {
      return new StringBuilder().append("Badge Count                       : ").append(Recipient.self().getBadges().size()).append("\n")
                                .append("ExpiredBadge                      : ").append(WaveStore.inAppPayments().getExpiredBadge() != null).append("\n")
                                .append("LastKeepAliveLaunchTime           : ").append(WaveStore.inAppPayments().getLastKeepAliveLaunchTime()).append("\n")
                                .append("LastEndOfPeriod                   : ").append(WaveStore.inAppPayments().getLastEndOfPeriod()).append("\n")
                                .append("InAppPayment.State                : ").append(latestRecurringDonation.getState()).append("\n")
                                .append("InAppPayment.EndOfPeriod          : ").append(latestRecurringDonation.getEndOfPeriodSeconds()).append("\n")
                                .append("InAppPaymentData.PaymentMethodType: ").append(getPaymentMethod(latestRecurringDonation.getData())).append("\n")
                                .append("InAppPaymentData.RedemptionState  : ").append(getRedemptionStage(latestRecurringDonation.getData())).append("\n")
                                .append("InAppPaymentData.Error            : ").append(getError(latestRecurringDonation.getData())).append("\n")
                                .append("InAppPaymentData.Cancellation     : ").append(getCancellation(latestRecurringDonation.getData())).append("\n")
                                .append("DisplayBadgesOnProfile            : ").append(WaveStore.inAppPayments().getDisplayBadgesOnProfile()).append("\n")
                                .append("ShouldCancelBeforeNextAttempt     : ").append(InAppPaymentsRepository.getShouldCancelSubscriptionBeforeNextSubscribeAttempt(InAppPaymentSubscriberRecord.Type.DONATION)).append("\n")
                                .append("IsUserManuallyCancelledDonation   : ").append(WaveStore.inAppPayments().isDonationSubscriptionManuallyCancelled()).append("\n");

    } else {
      return new StringBuilder().append("Badge Count                             : ").append(Recipient.self().getBadges().size()).append("\n")
                                .append("ExpiredBadge                            : ").append(WaveStore.inAppPayments().getExpiredBadge() != null).append("\n")
                                .append("LastKeepAliveLaunchTime                 : ").append(WaveStore.inAppPayments().getLastKeepAliveLaunchTime()).append("\n")
                                .append("LastEndOfPeriod                         : ").append(WaveStore.inAppPayments().getLastEndOfPeriod()).append("\n")
                                .append("IsUserManuallyCancelledDonation         : ").append(WaveStore.inAppPayments().isDonationSubscriptionManuallyCancelled()).append("\n")
                                .append("DisplayBadgesOnProfile                  : ").append(WaveStore.inAppPayments().getDisplayBadgesOnProfile()).append("\n")
                                .append("SubscriptionRedemptionFailed            : ").append(WaveStore.inAppPayments().getSubscriptionRedemptionFailed()).append("\n")
                                .append("ShouldCancelBeforeNextAttempt           : ").append(WaveStore.inAppPayments().getShouldCancelSubscriptionBeforeNextSubscribeAttempt()).append("\n");
    }
  }

  private @NonNull String getPaymentMethod(@NonNull InAppPaymentData inAppPaymentData) {
    return inAppPaymentData.paymentMethodType.toString();
  }

  private @NonNull String getRedemptionStage(@NonNull InAppPaymentData inAppPaymentData) {
    if (inAppPaymentData.redemption == null) {
      return "null";
    } else {
      return inAppPaymentData.redemption.stage.name();
    }
  }

  private @NonNull String getError(@NonNull InAppPaymentData inAppPaymentData) {
    if (inAppPaymentData.error == null) {
      return "none";
    } else {
      return inAppPaymentData.error.toString();
    }
  }

  private @NonNull String getCancellation(@NonNull InAppPaymentData inAppPaymentData) {
    if (inAppPaymentData.cancellation == null) {
      return "none";
    } else {
      return inAppPaymentData.cancellation.reason.name();
    }
  }
}

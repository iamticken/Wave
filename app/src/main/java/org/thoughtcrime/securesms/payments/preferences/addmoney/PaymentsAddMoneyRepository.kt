package org.thoughtcrime.securesms.payments.preferences.addmoney

import androidx.annotation.MainThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.wave.core.util.Result as WaveResult

internal class PaymentsAddMoneyRepository {
  @MainThread
  fun getWalletAddress(): Single<WaveResult<AddressAndUri, Error>> {
    if (!WaveStore.payments.mobileCoinPaymentsEnabled()) {
      return Single.just(WaveResult.failure(Error.PAYMENTS_NOT_ENABLED))
    }

    return Single.fromCallable<WaveResult<AddressAndUri, Error>> {
      val publicAddress = AppDependencies.payments.wallet.mobileCoinPublicAddress
      val paymentAddressBase58 = publicAddress.paymentAddressBase58
      val paymentAddressUri = publicAddress.paymentAddressUri
      WaveResult.success(AddressAndUri(paymentAddressBase58, paymentAddressUri))
    }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
  }

  internal enum class Error {
    PAYMENTS_NOT_ENABLED
  }
}

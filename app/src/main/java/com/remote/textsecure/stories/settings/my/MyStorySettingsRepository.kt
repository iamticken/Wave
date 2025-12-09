package org.thoughtcrime.securesms.stories.settings.my

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyData
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipState

class MyStorySettingsRepository {

  fun getPrivacyState(): Single<MyStoryPrivacyState> {
    return Single.fromCallable {
      getStoryPrivacyState()
    }.subscribeOn(Schedulers.io())
  }

  fun observeChooseInitialPrivacy(): Observable<ChooseInitialMyStoryMembershipState> {
    return Single
      .fromCallable { WaveDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)!! }
      .subscribeOn(Schedulers.io())
      .flatMapObservable { recipientId ->
        val allWaveConnectionsCount = getAllWaveConnectionsCount().toObservable()
        val stateWithoutCount = Recipient.observable(recipientId)
          .flatMap { Observable.just(ChooseInitialMyStoryMembershipState(recipientId = recipientId, privacyState = getStoryPrivacyState())) }

        Observable.combineLatest(allWaveConnectionsCount, stateWithoutCount) { count, state -> state.copy(allWaveConnectionsCount = count) }
      }
  }

  fun setPrivacyMode(privacyMode: DistributionListPrivacyMode): Completable {
    return Completable.fromAction {
      WaveDatabase.distributionLists.setPrivacyMode(DistributionListId.MY_STORY, privacyMode)
      Stories.onStorySettingsChanged(DistributionListId.MY_STORY)
    }.subscribeOn(Schedulers.io())
  }

  fun getRepliesAndReactionsEnabled(): Single<Boolean> {
    return Single.fromCallable {
      WaveDatabase.distributionLists.getStoryType(DistributionListId.MY_STORY).isStoryWithReplies
    }.subscribeOn(Schedulers.io())
  }

  fun setRepliesAndReactionsEnabled(repliesAndReactionsEnabled: Boolean): Completable {
    return Completable.fromAction {
      WaveDatabase.distributionLists.setAllowsReplies(DistributionListId.MY_STORY, repliesAndReactionsEnabled)
      Stories.onStorySettingsChanged(DistributionListId.MY_STORY)
    }.subscribeOn(Schedulers.io())
  }

  fun getAllWaveConnectionsCount(): Single<Int> {
    return Single.fromCallable {
      WaveDatabase.recipients.getWaveContactsCount(RecipientTable.IncludeSelfMode.Exclude)
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  private fun getStoryPrivacyState(): MyStoryPrivacyState {
    val privacyData: DistributionListPrivacyData = WaveDatabase.distributionLists.getPrivacyData(DistributionListId.MY_STORY)

    return MyStoryPrivacyState(
      privacyMode = privacyData.privacyMode,
      connectionCount = privacyData.memberCount
    )
  }
}

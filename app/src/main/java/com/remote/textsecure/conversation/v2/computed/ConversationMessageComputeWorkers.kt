/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.conversation.v2.computed

import android.content.Context
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.wave.core.util.ThreadUtil
import org.wave.core.util.concurrent.WaveExecutors
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement

/**
 * Collection of workers to recompute computed ConversationMessage fields.
 */
object ConversationMessageComputeWorkers {

  private val executor = WaveExecutors.newCachedSingleThreadExecutor("conversation-message-compute", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)

  fun recomputeFormattedDate(
    context: Context,
    items: List<ConversationMessageElement>,
    forceUpdate: Boolean = false
  ): Single<Boolean> {
    return Single.fromCallable {
      var hasUpdatedProperties = false
      for (item in items) {
        val oldDate = item.conversationMessage.computedProperties.formattedDate
        if (oldDate.isRelative || forceUpdate) {
          val newDate = ConversationMessage.getFormattedDate(context, item.conversationMessage.messageRecord)
          item.conversationMessage.computedProperties.formattedDate = newDate
          hasUpdatedProperties = true
        }
      }

      hasUpdatedProperties
    }.subscribeOn(Schedulers.from(executor))
  }
}

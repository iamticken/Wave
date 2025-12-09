package org.thoughtcrime.securesms.stories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import org.wave.core.util.concurrent.WaveExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.fragments.requireListener

/**
 * Displays an education sheet to the user which explains what Group Stories are.
 */
class GroupStoryEducationSheet : FixedRoundedCornerBottomSheetDialogFragment() {

  companion object {
    const val KEY = "GROUP_STORY_EDU"
  }

  override val peekHeightPercentage: Float = 1f

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.group_story_education_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    WaveStore.story.userHasSeenGroupStoryEducationSheet = true
    WaveExecutors.BOUNDED_IO.execute { Stories.onStorySettingsChanged(Recipient.self().id) }

    view.findViewById<MaterialButton>(R.id.next).setOnClickListener {
      requireListener<Callback>().onGroupStoryEducationSheetNext()
      dismissAllowingStateLoss()
    }
  }

  interface Callback {
    fun onGroupStoryEducationSheetNext()
  }
}

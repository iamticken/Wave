package org.thoughtcrime.securesms.messages

import org.wave.core.util.Base64
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.whispersystems.waveservice.api.messages.WaveServicePreview
import org.whispersystems.waveservice.api.messages.WaveServiceTextAttachment
import java.io.IOException
import java.util.Optional
import kotlin.math.roundToInt

object StorySendUtil {
  @JvmStatic
  @Throws(IOException::class)
  fun deserializeBodyToStoryTextAttachment(message: OutgoingMessage, getPreviewsFor: (OutgoingMessage) -> List<WaveServicePreview>): WaveServiceTextAttachment {
    val storyTextPost = StoryTextPost.ADAPTER.decode(Base64.decode(message.body))
    val preview = if (message.linkPreviews.isEmpty()) {
      Optional.empty()
    } else {
      Optional.of(getPreviewsFor(message)[0])
    }

    return if (storyTextPost.background!!.linearGradient != null) {
      WaveServiceTextAttachment.forGradientBackground(
        Optional.ofNullable(storyTextPost.body),
        Optional.ofNullable(getStyle(storyTextPost.style)),
        Optional.of(storyTextPost.textForegroundColor),
        Optional.of(storyTextPost.textBackgroundColor),
        preview,
        WaveServiceTextAttachment.Gradient(
          Optional.of(storyTextPost.background.linearGradient!!.rotation.roundToInt()),
          ArrayList(storyTextPost.background.linearGradient.colors),
          ArrayList(storyTextPost.background.linearGradient.positions)
        )
      )
    } else {
      WaveServiceTextAttachment.forSolidBackground(
        Optional.ofNullable(storyTextPost.body),
        Optional.ofNullable(getStyle(storyTextPost.style)),
        Optional.of(storyTextPost.textForegroundColor),
        Optional.of(storyTextPost.textBackgroundColor),
        preview,
        storyTextPost.background.singleColor!!.color
      )
    }
  }

  private fun getStyle(style: StoryTextPost.Style): WaveServiceTextAttachment.Style {
    return when (style) {
      StoryTextPost.Style.REGULAR -> WaveServiceTextAttachment.Style.REGULAR
      StoryTextPost.Style.BOLD -> WaveServiceTextAttachment.Style.BOLD
      StoryTextPost.Style.SERIF -> WaveServiceTextAttachment.Style.SERIF
      StoryTextPost.Style.SCRIPT -> WaveServiceTextAttachment.Style.SCRIPT
      StoryTextPost.Style.CONDENSED -> WaveServiceTextAttachment.Style.CONDENSED
      else -> WaveServiceTextAttachment.Style.DEFAULT
    }
  }
}

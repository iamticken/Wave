package org.thoughtcrime.securesms.components.menu

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import org.thoughtcrime.securesms.R

/**
 * Represents an action to be rendered via [WaveContextMenu] or [WaveBottomActionBar]
 */
data class ActionItem @JvmOverloads constructor(
  @DrawableRes val iconRes: Int,
  val title: CharSequence,
  @ColorRes val tintRes: Int = R.color.wave_colorOnSurface,
  val action: Runnable
)

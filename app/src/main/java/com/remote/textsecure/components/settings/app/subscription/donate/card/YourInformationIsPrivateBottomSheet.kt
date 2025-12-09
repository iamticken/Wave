package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import org.wave.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure

/**
 * Displays information about how Wave keeps card details private and how
 * Wave does not link donation information to your Wave account.
 */
class YourInformationIsPrivateBottomSheet : DSLSettingsBottomSheetFragment() {
  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    return configure {
      space(10.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__your_information_is_private,
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TextAppearanceModifier(R.style.Wave_Text_HeadlineMedium)
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__wave_does_not_collect,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__we_use_stripe,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__wave_does_not_and_cannot,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__thank_you,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(56.dp)
    }
  }
}

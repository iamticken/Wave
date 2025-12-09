package org.thoughtcrime.securesms.backup.v2.ui.verify

import android.app.Activity.RESULT_OK
import androidx.compose.runtime.Composable
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.WaveStore

/**
 * Fragment to confirm the backup key just shown after users forget it.
 */
class ConfirmBackupKeyDisplayFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    MessageBackupsKeyVerifyScreen(
      backupKey = WaveStore.account.accountEntropyPool.displayValue,
      onNavigationClick = {
        requireActivity().supportFragmentManager.popBackStack()
      },
      onNextClick = {
        WaveStore.backup.lastVerifyKeyTime = System.currentTimeMillis()
        WaveStore.backup.hasVerifiedBefore = true
        WaveStore.backup.hasSnoozedVerified = false
        requireActivity().setResult(RESULT_OK)
        requireActivity().finish()
      }
    )
  }
}

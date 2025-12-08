package org.thoughtcrime.securesms.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import org.thoughtcrime.securesms.WaveInstrumentationApplicationContext

/**
 * Custom runner that replaces application with [WaveInstrumentationApplicationContext].
 */
@Suppress("unused")
class WaveTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
    return super.newApplication(cl, WaveInstrumentationApplicationContext::class.java.name, context)
  }
}

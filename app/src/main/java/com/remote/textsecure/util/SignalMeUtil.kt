package org.thoughtcrime.securesms.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

internal object WaveMeUtil {
  private val E164_REGEX = """^(https|sgnl)://wave\.me/#p/(\+[0-9]+)$""".toRegex()

  /**
   * If this is a valid wave.me link and has a valid e164, it will return the e164. Otherwise, it will return null.
   */
  @JvmStatic
  fun parseE164FromLink(link: String?): String? {
    if (link.isNullOrBlank()) {
      return null
    }

    return E164_REGEX.find(link)?.let { match ->
      val e164: String = match.groups[2]?.value ?: return@let null

      if (PhoneNumberUtil.getInstance().isPossibleNumber(e164, Locale.getDefault().country)) {
        WaveE164Util.formatAsE164(e164)
      } else {
        null
      }
    }
  }
}

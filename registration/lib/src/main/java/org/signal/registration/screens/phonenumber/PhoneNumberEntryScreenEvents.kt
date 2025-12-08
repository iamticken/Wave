/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.screens.phonenumber

sealed interface PhoneNumberEntryScreenEvents {
  data class CountryCodeChanged(val value: String) : PhoneNumberEntryScreenEvents
  data class PhoneNumberChanged(val value: String) : PhoneNumberEntryScreenEvents
  data object PhoneNumberSubmitted : PhoneNumberEntryScreenEvents
  data object CountryPicker : PhoneNumberEntryScreenEvents
  data class CaptchaCompleted(val token: String) : PhoneNumberEntryScreenEvents
  data object ConsumeOneTimeEvent : PhoneNumberEntryScreenEvents
}

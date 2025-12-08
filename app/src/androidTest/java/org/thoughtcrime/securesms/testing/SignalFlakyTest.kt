/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

@Retention(AnnotationRetention.RUNTIME)
annotation class WaveFlakyTest(val allowedAttempts: Int = 3)

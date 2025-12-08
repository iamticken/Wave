/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.rules.ExternalResource
import org.wave.core.util.concurrent.WaveDispatchers

/**
 * Rule that allows for injection of test dispatchers when operating with ViewModels.
 */
class CoroutineDispatcherRule(
  defaultDispatcher: TestDispatcher,
  mainDispatcher: TestDispatcher = defaultDispatcher,
  ioDispatcher: TestDispatcher = defaultDispatcher,
  unconfinedDispatcher: TestDispatcher = defaultDispatcher
) : ExternalResource() {

  private val testDispatcherProvider = TestDispatcherProvider(
    main = mainDispatcher,
    io = ioDispatcher,
    default = defaultDispatcher,
    unconfined = unconfinedDispatcher
  )

  override fun before() {
    WaveDispatchers.setDispatcherProvider(testDispatcherProvider)
  }

  override fun after() {
    WaveDispatchers.setDispatcherProvider()
  }

  private class TestDispatcherProvider(
    override val main: CoroutineDispatcher,
    override val io: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
    override val unconfined: CoroutineDispatcher
  ) : WaveDispatchers.DispatcherProvider
}

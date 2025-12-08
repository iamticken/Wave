/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.wave.core.util.logging.Log

/**
 * A JUnit rule that retries tests annotated with [WaveFlakyTest] before considering them to be a failure.
 * As the name implies, this is useful for known-flaky tests.
 */
class WaveFlakyTestRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    val flakyAnnotation = description.getAnnotation(WaveFlakyTest::class.java)

    return if (flakyAnnotation != null) {
      FlakyStatement(
        base = base,
        description = description,
        allowedAttempts = flakyAnnotation.allowedAttempts
      )
    } else {
      base
    }
  }

  private class FlakyStatement(private val base: Statement, private val description: Description, private val allowedAttempts: Int) : Statement() {
    override fun evaluate() {
      var attemptsRemaining = allowedAttempts
      while (attemptsRemaining > 0) {
        try {
          base.evaluate()
          return
        } catch (t: Throwable) {
          attemptsRemaining--
          if (attemptsRemaining <= 0) {
            throw t
          }
          Log.w(description.testClass.simpleName, "[${description.methodName}] Flaky test failed! $attemptsRemaining attempt(s) remaining.", t)
        }
      }
    }
  }
}

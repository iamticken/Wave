/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.registration.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.wave.core.ui.compose.theme.WaveTheme
import org.wave.core.ui.navigation.ResultEffect
import org.wave.core.ui.navigation.ResultEventBus
import org.wave.registration.RegistrationActivity
import org.wave.registration.sample.MainActivity.Companion.REGISTRATION_RESULT
import org.wave.registration.sample.screens.RegistrationCompleteScreen
import org.wave.registration.sample.screens.main.MainScreen
import org.wave.registration.sample.screens.main.MainScreenViewModel

/**
 * Navigation routes for the sample app.
 */
sealed interface SampleRoute : NavKey {
  @Serializable
  data object Main : SampleRoute

  @Serializable
  data object RegistrationComplete : SampleRoute
}

/**
 * Sample app activity that launches the registration flow for testing.
 */
class MainActivity : ComponentActivity() {
  companion object {
    const val REGISTRATION_RESULT = "registration_result"
  }

  private val viewModel: AppViewModel by viewModels()

  private val registrationLauncher: ActivityResultLauncher<Unit> = registerForActivityResult(RegistrationActivity.RegistrationContract()) { success ->
    viewModel.resultEventBus.sendResult(REGISTRATION_RESULT, success)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      WaveTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val backStack = rememberNavBackStack(SampleRoute.Main)

          SampleNavHost(
            onLaunchRegistration = { registrationLauncher.launch(Unit) },
            backStack = backStack,
            resultEventBus = viewModel.resultEventBus,
            onStartOver = {
              backStack.clear()
              backStack.add(SampleRoute.Main)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun SampleNavHost(
  onLaunchRegistration: () -> Unit,
  onStartOver: () -> Unit,
  backStack: NavBackStack<NavKey>,
  resultEventBus: ResultEventBus,
  modifier: Modifier = Modifier
) {
  val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<SampleRoute.Main> {
      val viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(onLaunchRegistration)
      )
      val state by viewModel.state.collectAsStateWithLifecycle()

      ResultEffect<Boolean>(resultEventBus, REGISTRATION_RESULT) { success ->
        if (success) {
          backStack.add(SampleRoute.RegistrationComplete)
        }
      }

      MainScreen(
        state = state,
        onEvent = { viewModel.onEvent(it) }
      )
    }

    entry<SampleRoute.RegistrationComplete> {
      RegistrationCompleteScreen(onStartOver = onStartOver)
    }
  }

  val decorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<NavKey>()
  )

  val entries = rememberDecoratedNavEntries(
    backStack = backStack,
    entryDecorators = decorators,
    entryProvider = entryProvider
  )

  NavDisplay(
    entries = entries,
    onBack = {},
    modifier = modifier
  )
}

package com.invokeil.shinigami.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.invokeil.shinigami.core.overlay.AssistantSessionCoordinator
import com.invokeil.shinigami.core.overlay.AssistantOverlayState
import com.invokeil.shinigami.core.overlay.OverlayPrefs
import com.invokeil.shinigami.core.overlay.ui.OverlaySessionContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Transparent trampoline used by the software wake-word path (OVERLAY SPEC
 * §2, §4). Shows the assistant overlay ABOVE the current app — the user's
 * activity stays visible and intact underneath — and finishes as soon as
 * the session ends. The full MainActivity is never opened automatically.
 */
@AndroidEntryPoint
class AssistantTrampolineActivity : ComponentActivity() {

    @Inject lateinit var coordinator: AssistantSessionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by coordinator.prefsRef.collectAsState(initial = OverlayPrefs())
            val state by coordinator.controllerRef.state.collectAsState()

            LaunchedEffect(Unit) { coordinator.beginSession() }
            LaunchedEffect(state) {
                if (state is AssistantOverlayState.Hidden) finish()
            }

            OverlaySessionContent(
                controller = coordinator.controllerRef,
                prefs = prefs,
                onExpandToApp = {
                    coordinator.endSession()
                    finish()
                },
                onDismiss = {
                    coordinator.endSession()
                    finish()
                },
            )
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Back dismisses the overlay (§18) — never falls through to the app below.
        coordinator.endSession()
        finish()
    }
}

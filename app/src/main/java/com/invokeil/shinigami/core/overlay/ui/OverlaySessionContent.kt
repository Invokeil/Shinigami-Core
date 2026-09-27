package com.invokeil.shinigami.core.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.invokeil.shinigami.core.overlay.ui.AssistantOverlayHost
import com.invokeil.shinigami.core.overlay.OverlayPrefs
import com.invokeil.shinigami.core.overlay.AssistantOverlayController

/**
 * Transparent hosting surface used by the VoiceInteractionSession window.
 * Kept intentionally tiny — all state flows through the controller.
 */
@Composable
fun OverlaySessionContent(
    controller: AssistantOverlayController,
    prefs: OverlayPrefs,
    onExpandToApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x26000000), Color(0x3D000000)),
                ),
            ),
    ) {
        AssistantOverlayHost(
            controller = controller,
            prefs = prefs,
            onExpandToApp = onExpandToApp,
            onDismiss = onDismiss,
        )
    }
}

package com.invokeil.shinigami.core.overlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.invokeil.shinigami.core.device.DeviceProfile
import com.invokeil.shinigami.core.device.rememberReducedMotion
import com.invokeil.shinigami.core.overlay.AssistantOverlayState
import com.invokeil.shinigami.core.overlay.AssistantOverlayController
import com.invokeil.shinigami.core.overlay.OverlayPrefs
import com.invokeil.shinigami.core.overlay.OverlayStyle

// --------------------------------------------------------------- palette ----
private val ShiScrim = Color(0xB00A0A10)
private val ShiCard = Color(0xE612121A)
private val ShiCardSoft = Color(0xCC161622)
private val ShiCrimson = Color(0xFFF43F5E)
private val ShiMagenta = Color(0xFFD946EF)
private val ShiViolet = Color(0xFF8B5CF6)
private val ShiText = Color(0xFFF4F4F8)
private val ShiTextDim = Color(0xFF9B9BAF)

/**
 * Root overlay host (OVERLAY SPEC §40). Renders the whole assistant surface
 * from the controller state only — no provider knowledge (§42).
 */
@Composable
fun AssistantOverlayHost(
    controller: AssistantOverlayController,
    prefs: OverlayPrefs,
    onExpandToApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val amplitude by controller.micAmplitude.collectAsState()
    val stopAvailable by controller.stopAvailable.collectAsState()

    val visible = state.visible
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .pointerInput(Unit) {
                // swipe-down dismisses when appropriate (§22, §18)
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 0 && state !is AssistantOverlayState.Executing) {
                        change.consume()
                        onDismiss()
                    }
                }
            },
        contentAlignment = when (prefs.position) {
            com.invokeil.shinigami.core.overlay.OverlayPosition.BOTTOM -> Alignment.BottomCenter
            else -> Alignment.Center
        },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f),
            ),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f),
        ) {
            AssistantCard(
                state = state,
                amplitude = amplitude,
                prefs = prefs,
                stopAvailable = stopAvailable,
                controller = controller,
                onExpandToApp = onExpandToApp,
                onDismiss = onDismiss,
            )
        }
    }
}

// ------------------------------------------------------------------ card ----
@Composable
private fun AssistantCard(
    state: AssistantOverlayState,
    amplitude: Float,
    prefs: OverlayPrefs,
    stopAvailable: Boolean,
    controller: AssistantOverlayController,
    onExpandToApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isMinimal = prefs.style == OverlayStyle.MINIMAL
    val showFullCard = when {
        state is AssistantOverlayState.Error -> true
        state is AssistantOverlayState.ConfirmationRequired -> true
        state is AssistantOverlayState.Responding && prefs.style == OverlayStyle.COMPACT -> true
        state is AssistantOverlayState.Executing && prefs.showActionProgress -> true
        state is AssistantOverlayState.Transcribing && prefs.showTranscript -> true
        else -> false
    }
    val maxCardWidth = if (LocalConfiguration.current.screenWidthDp >= 720) 480.dp else 360.dp

    Column(
        modifier = Modifier
            .padding(bottom = 24.dp)
            .widthIn(max = maxCardWidth)
            .clip(RoundedCornerShape(28.dp))
            .background(ShiCardSoft)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Orb (the visual identity, §44)
        AssistantOrb(
            amplitude = amplitude,
            stateKind = state,
            reducedMotion = rememberReducedMotion(),
            quality = prefs.animationQuality,
            modifier = Modifier
                .size(if (isMinimal) 84.dp else 116.dp)
                .clickable {
                    when (state) {
                        is AssistantOverlayState.Listening,
                        is AssistantOverlayState.Transcribing,
                        -> controller.startListening()
                        is AssistantOverlayState.Hidden -> controller.startListening()
                        else -> Unit
                    }
                },
        )

        Spacer(Modifier.height(10.dp))

        Crossfade(targetState = state, animationSpec = tween(150), label = "status") { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (s) {
                    is AssistantOverlayState.Hidden -> Unit
                    is AssistantOverlayState.Activating -> AssistantStatusText("Shini…")
                    is AssistantOverlayState.Listening -> AssistantStatusText("Listening…")
                    is AssistantOverlayState.Transcribing -> {
                        AssistantStatusText(if (prefs.showTranscript) "Listening…" else "Listening…")
                        if (prefs.showTranscript) {
                            Spacer(Modifier.height(4.dp))
                            AssistantTranscript(s.partialText)
                        }
                    }
                    is AssistantOverlayState.Thinking -> AssistantStatusText("Thinking…")
                    is AssistantOverlayState.Responding -> {
                        AssistantTranscript(s.text, expandedStyle = s.isFinal)
                        if (s.isFinal && s.text.length > 220) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = onExpandToApp) { Text("Continue Reading", color = ShiCrimson) }
                        }
                    }
                    is AssistantOverlayState.Executing -> {
                        if (prefs.showActionProgress) {
                            AssistantActionProgress(s.actionName, s.step, s.totalSteps)
                        } else {
                            AssistantStatusText(s.actionName)
                        }
                    }
                    is AssistantOverlayState.ConfirmationRequired -> AssistantConfirmationCard(
                        title = s.title,
                        description = s.description,
                        onApprove = { controller.confirmPending(true) },
                        onDeny = { controller.confirmPending(false) },
                    )
                    is AssistantOverlayState.Error -> AssistantErrorCard(
                        message = s.message,
                        technical = s.technical,
                        actionLabel = s.actionLabel,
                        onAction = onExpandToApp,
                        onDismiss = onDismiss,
                    )
                }
            }
        }

        // Bottom control row (§22): mic / keyboard / expand / X  (+ STOP §55)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stopAvailable) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF7F1D1D))
                        .clickable { controller.emergencyStop() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.widthIn(min = 6.dp))
                        Text("STOP", color = Color.White, fontSize = 13.sp, letterSpacing = 1.5.sp)
                    }
                }
                Spacer(Modifier.widthIn(min = 10.dp))
            }
            IconButton(onClick = { controller.startListening() }) {
                Icon(Icons.Filled.Mic, "Listen", tint = ShiTextDim)
            }
            IconButton(onClick = onExpandToApp) {
                Icon(Icons.Filled.Keyboard, "Text input", tint = ShiTextDim)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "Dismiss", tint = ShiTextDim)
            }
        }
    }
}

// ------------------------------------------------------------------- orb ----
/**
 * The Shinigami orb: the provided Lottie animation, mic-amplitude reactive
 * scale 1.00→1.08 (§9), quality tiers (§30) and reduced-motion respect (§31).
 */
@Composable
fun AssistantOrb(
    amplitude: Float,
    stateKind: AssistantOverlayState,
    reducedMotion: Boolean,
    quality: com.invokeil.shinigami.core.overlay.AnimationQuality,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(
            com.invokeil.shinigami.R.raw.shinigami_assistant_animation,
        ),
    )
    val useLottie = when (quality) {
        com.invokeil.shinigami.core.overlay.AnimationQuality.HIGH -> true
        com.invokeil.shinigami.core.overlay.AnimationQuality.BATTERY_SAVER -> false
        com.invokeil.shinigami.core.overlay.AnimationQuality.AUTO ->
            !DeviceProfile.isLowRam(context) && !reducedMotion
    }
    val targetScale = if (reducedMotion) 1f else 1f + amplitude * 0.08f
    val scale by animateFloatAsState(targetScale, spring(stiffness = 300f, dampingRatio = 0.55f), label = "orbScale")

    // ambient glow behind the orb
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val glowAlpha = (0.25f + amplitude * 0.45f).coerceAtMost(0.7f)
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer { alpha = if (reducedMotion) 0.2f else glowAlpha }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ShiCrimson.copy(alpha = 0.28f), Color.Transparent),
                    ),
                ),
        )
        if (useLottie && composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = Integer.MAX_VALUE,
                speed = 1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
            )
        } else {
            StaticOrb(modifier = Modifier.fillMaxWidth().scale(scale))
        }
    }
}

/** Low-power fallback orb (§30) — pure Canvas, no Lottie runtime. */
@Composable
private fun StaticOrb(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFF701A75)),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f)),
        )
    }
}

// ---------------------------------------------------------------- pieces ----
@Composable
private fun AssistantStatusText(text: String) {
    Text(text, color = ShiTextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun AssistantTranscript(text: String, expandedStyle: Boolean = false) {
    if (text.isBlank()) return
    Text(
        "“$text”",
        color = ShiText,
        fontSize = if (expandedStyle) 15.sp else 14.sp,
        textAlign = TextAlign.Center,
        maxLines = if (expandedStyle) 5 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AssistantActionProgress(name: String, step: Int, totalSteps: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = ShiText, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (totalSteps > 1) {
            Spacer(Modifier.height(3.dp))
            Text("Step $step of $totalSteps", color = ShiTextDim, fontSize = 12.sp)
        } else {
            Spacer(Modifier.height(3.dp))
            Text("✓", color = Color(0xFF4ADE80), fontSize = 13.sp)
        }
    }
}

@Composable
private fun AssistantConfirmationCard(
    title: String,
    description: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ShiCard)
            .padding(14.dp),
    ) {
        Text(title, color = ShiText, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(description, color = ShiTextDim, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDeny) { Text("Cancel", color = ShiTextDim) }
            Spacer(Modifier.widthIn(min = 8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(ShiCrimson)
                    .clickable(onClick = onApprove)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text("Confirm", color = Color.White, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun AssistantErrorCard(
    message: String,
    technical: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showTech by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ShiCard)
            .padding(14.dp),
    ) {
        Text(message, color = Color(0xFFFCA5A5), fontSize = 14.sp)
        if (technical != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (showTech) "Hide technical details" else "Technical details",
                color = ShiTextDim,
                fontSize = 12.sp,
                modifier = Modifier.clickable { showTech = !showTech },
            )
            if (showTech) {
                Text(technical, color = ShiTextDim, fontSize = 11.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Dismiss", color = ShiTextDim) }
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = ShiCrimson) }
            }
        }
    }
}

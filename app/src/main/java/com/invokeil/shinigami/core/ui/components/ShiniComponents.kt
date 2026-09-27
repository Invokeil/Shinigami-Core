package com.invokeil.shinigami.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ---------------------------------------------------------------------------
 * Reusable visual vocabulary: the Shini orb, soft cards, tool execution
 * cards, empty states. Animations are spring-based and subtle — premium,
 * never flashy, and fully disabled when "reduce animations" is on.
 * --------------------------------------------------------------------------*/

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR }

/** The signature Shini orb: a glowing gradient sphere that reacts to state. */
@Composable
fun ShiniOrb(
    state: OrbState,
    size: Dp = 84.dp,
    reduceAnimations: Boolean = false,
    onClick: (() -> Unit)?,
    contentDescriptionText: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceAnimations) 1f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (reduceAnimations || state == OrbState.IDLE) 0.35f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceAnimations || state != OrbState.THINKING) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
        ),
        label = "rotation",
    )

    val active = state != OrbState.IDLE && state != OrbState.ERROR
    val primaryColor = when (state) {
        OrbState.ERROR -> MaterialTheme.colorScheme.error
        OrbState.EXECUTING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val scale by animateFloatAsState(
        targetValue = if (active) pulse else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f),
        label = "scale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + 26.dp)
                .semantics { contentDescription = contentDescriptionText }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onClick != null,
                ) { onClick?.invoke() },
        ) {
            // Halo
            Box(
                Modifier
                    .size(size + 22.dp)
                    .alpha(if (active) haloAlpha else 0.16f)
                    .scale(if (active) scale * 1.04f else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.45f), Color.Transparent),
                        ),
                    ),
            )
            // Core
            Box(
                Modifier
                    .size(size)
                    .scale(scale)
                    .shadow(18.dp, CircleShape, ambientColor = primaryColor, spotColor = primaryColor)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    OrbState.ERROR -> Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.42f),
                    )

                    OrbState.EXECUTING -> Icon(
                        Icons.Rounded.Stop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.4f),
                    )

                    else -> Icon(
                        Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.4f),
                    )
                }
            }
        }
    }
}

/** Soft elevated card used across the app. */
@Composable
fun ShiniCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        content()
    }
}

/** "Opening Spotify… ✓" — a live status card for every device action. */
@Composable
fun ToolExecutionCard(
    label: String,
    state: ToolCardState,
    modifier: Modifier = Modifier,
) {
    val tint = when (state) {
        ToolCardState.RUNNING -> MaterialTheme.colorScheme.primary
        ToolCardState.DONE -> MaterialTheme.colorScheme.tertiary
        ToolCardState.FAILED -> MaterialTheme.colorScheme.error
    }
    ShiniCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            when (state) {
                ToolCardState.RUNNING -> PulsingDot(color = tint)

                ToolCardState.DONE -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )

                ToolCardState.FAILED -> Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = when (state) {
                    ToolCardState.RUNNING -> label
                    ToolCardState.DONE -> "$label ✓"
                    ToolCardState.FAILED -> "$label — failed"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state == ToolCardState.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

enum class ToolCardState { RUNNING, DONE, FAILED }

@Composable
fun PulsingDot(color: Color, size: Dp = 12.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "dotScale",
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color),
    )
}

/** Friendly empty state with an optional CTA. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    ctaText: String? = null,
    onCta: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (ctaText != null && onCta != null) {
            Spacer(Modifier.height(20.dp))
            ShiniButton(text = ctaText, onClick = onCta)
        }
    }
}

/** Primary pill button with press feedback. */
@Composable
fun ShiniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(200),
        label = "btnBg",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 13.dp),
    ) {
        Text(
            text,
            color = if (enabled) androidx.compose.ui.graphics.Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Small status chip (provider state, permission mode…). */
@Composable
fun StatusChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Section header used in Settings & dashboards. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp, start = 4.dp),
    )
}

/** Thin top divider with breathing space. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .alpha(0.5f)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    )
}

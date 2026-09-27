package com.invokeil.shinigami.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.invokeil.shinigami.core.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = ReaperPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = ReaperPrimaryContainer,
    onPrimaryContainer = ReaperOnPrimaryContainer,
    secondary = ReaperAccent,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF14121F),
    background = ReaperBg,
    onBackground = ReaperTextPrimary,
    surface = ReaperBgElevated,
    onSurface = ReaperTextPrimary,
    surfaceVariant = ReaperSurface,
    onSurfaceVariant = ReaperTextSecondary,
    surfaceContainerHighest = ReaperSurfaceHigh,
    outline = ReaperOutline,
    error = ReaperError,
    tertiary = ReaperSuccess,
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightAccent,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF14121F),
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightBgElevated,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerHighest = LightSurfaceHigh,
    outline = LightOutline,
    error = LightError,
    tertiary = LightSuccess,
)

/** Colour tokens that exist outside the M3 scheme (glows, gradients). */
object ColorToken {
    val OnPrimaryDarkColor = androidx.compose.ui.graphics.Color.White
    val OnSecondaryDarkColor = androidx.compose.ui.graphics.Color(0xFF14121F)
}

val ShiniShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ShinigamiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = ShiniTypography,
        shapes = ShiniShapes,
        content = content,
    )
}

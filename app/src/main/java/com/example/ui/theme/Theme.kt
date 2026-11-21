package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalUseFallbackRippleImplementation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LvngDarkColorScheme = darkColorScheme(
    primary = LvngCrimson,
    onPrimary = Color.White,
    primaryContainer = LvngCrimsonDark,
    onPrimaryContainer = LvngCrimsonSoft,
    secondary = LvngCyan,
    onSecondary = LvngVoid,
    secondaryContainer = Color(0xFF0A262D),
    onSecondaryContainer = LvngCyan,
    tertiary = LvngAmber,
    onTertiary = LvngVoid,
    background = LvngVoid,
    onBackground = LvngTextPrimary,
    surface = LvngObsidian,
    onSurface = LvngTextPrimary,
    surfaceVariant = LvngCarbon,
    onSurfaceVariant = LvngTextSecondary,
    surfaceContainerLowest = LvngVoid,
    surfaceContainerLow = LvngObsidian,
    surfaceContainer = LvngCarbon,
    surfaceContainerHigh = LvngElevated,
    surfaceContainerHighest = Color(0xFF23232F),
    outline = LvngBorderSubtle,
    outlineVariant = Color(0xFF1D1D28),
    error = Color(0xFFFF4B5C),
    onError = Color.White
)

val LvngShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LvngSpaceTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    // Use Compose fallback ripple implementation when running on non-hardware-accelerated canvases
    // or overlay windows to prevent RippleDrawable.STYLE_PATTERNED errors while keeping Material ripples.
    CompositionLocalProvider(LocalUseFallbackRippleImplementation provides true) {
        MaterialTheme(
            colorScheme = LvngDarkColorScheme,
            typography = LvngTypography,
            shapes = LvngShapes,
            content = content
        )
    }
}

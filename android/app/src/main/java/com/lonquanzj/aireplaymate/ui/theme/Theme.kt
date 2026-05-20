package com.lonquanzj.aireplaymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEEE8),
    onPrimaryContainer = Color(0xFF072E2B),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD6AE),
    onSecondaryContainer = Color(0xFF4A2500),
    tertiary = Color(0xFFA16207),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F0E5),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFE8DCCA),
    onSurfaceVariant = Color(0xFF51473B),
    outline = Color(0xFF857568)
)

@Composable
fun AiReplayMateTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

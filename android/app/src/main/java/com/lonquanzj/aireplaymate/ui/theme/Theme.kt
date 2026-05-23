package com.lonquanzj.aireplaymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3DC8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE5FF),
    onPrimaryContainer = Color(0xFF3F2B78),
    secondary = Color(0xFF7A659C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFEAF8),
    onSecondaryContainer = Color(0xFF3F2B78),
    tertiary = Color(0xFF7A57E8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3EEFF),
    onTertiaryContainer = Color(0xFF3F2B78),
    background = Color(0xFFFFFCFF),
    onBackground = Color(0xFF3F2B78),
    surface = Color(0xFFFFFCFF),
    onSurface = Color(0xFF3F2B78),
    surfaceVariant = Color(0xFFF8F4FF),
    onSurfaceVariant = Color(0xFF7A659C),
    outline = Color(0xFFA886FF),
    outlineVariant = Color(0xFFE1D7FF),
    error = Color(0xFFB3261E)
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

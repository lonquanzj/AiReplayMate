package com.lonquanzj.aireplaymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A45F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0EAFF),
    onPrimaryContainer = Color(0xFF2D196B),
    secondary = Color(0xFF248DC7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4F6FF),
    onSecondaryContainer = Color(0xFF123A54),
    tertiary = Color(0xFFC4862E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE6BC),
    onTertiaryContainer = Color(0xFF472700),
    background = Color(0xFFF8F5FF),
    onBackground = Color(0xFF241B35),
    surface = Color(0xFFFFFCFF),
    onSurface = Color(0xFF241B35),
    surfaceVariant = Color(0xFFEDE7FF),
    onSurfaceVariant = Color(0xFF5A4F72),
    outline = Color(0xFFB8A9DF),
    outlineVariant = Color(0xFFDCD2F4),
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

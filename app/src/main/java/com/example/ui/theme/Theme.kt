package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BlackHoleTheme(
    appTheme: AppTheme = AppTheme.ELEGANT_OBSIDIAN,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = darkColorScheme(
        primary = appTheme.primaryColor,
        onPrimary = if (appTheme == AppTheme.ELEGANT_OBSIDIAN || appTheme == AppTheme.TITANIUM_MONO) Color.Black else Color.White,
        primaryContainer = appTheme.surfaceColor,
        onPrimaryContainer = appTheme.accentColor,
        secondary = appTheme.accentColor,
        onSecondary = Color.Black,
        secondaryContainer = appTheme.surfaceColor,
        onSecondaryContainer = appTheme.primaryColor,
        tertiary = appTheme.accentColor,
        onTertiary = Color.Black,
        background = appTheme.backgroundColor,
        onBackground = TextPrimary,
        surface = appTheme.surfaceColor,
        onSurface = TextPrimary,
        surfaceVariant = appTheme.surfaceColor,
        onSurfaceVariant = TextSecondary,
        outline = appTheme.surfaceBorder,
        error = ErrorRed,
        onError = Color.White
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    BlackHoleTheme(appTheme = AppTheme.ELEGANT_OBSIDIAN, content = content)
}

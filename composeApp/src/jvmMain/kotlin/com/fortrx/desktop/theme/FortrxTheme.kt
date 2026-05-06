package com.fortrx.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FortrxAccent = Color(0xFF7C5CFF)
private val FortrxAccentDark = Color(0xFF6246EA)

private val DarkColors = darkColorScheme(
    primary = FortrxAccent,
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF0F1115),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1F242D)
)

private val LightColors = lightColorScheme(
    primary = FortrxAccentDark,
    background = Color(0xFFF7F7FB),
    surface = Color.White
)

@Composable
fun FortrxTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

package com.fortrx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val FortrxYellow = Color(0xFFFFC107)
val FortrxBlack = Color(0xFF000000)
val FortrxWhite = Color(0xFFFFFFFF)
val FortrxGray = Color(0xFFF5F5F5)
val FortrxDarkGray = Color(0xFF9E9E9E)

private val LightColorScheme = lightColorScheme(
    primary = FortrxYellow,
    onPrimary = FortrxBlack,
    secondary = FortrxBlack,
    onSecondary = FortrxWhite,
    background = FortrxWhite,
    onBackground = FortrxBlack,
    surface = FortrxWhite,
    onSurface = FortrxBlack,
    surfaceVariant = FortrxGray,
    onSurfaceVariant = FortrxBlack,
    error = Color(0xFFB00020),
    onError = FortrxWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = FortrxYellow,
    onPrimary = FortrxBlack,
    secondary = FortrxWhite,
    onSecondary = FortrxBlack,
    background = FortrxBlack,
    onBackground = FortrxWhite,
    surface = Color(0xFF121212),
    onSurface = FortrxWhite,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = FortrxWhite,
    error = Color(0xFFCF6679),
    onError = FortrxBlack
)

@Composable
fun FortrxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

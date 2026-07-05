package com.guardia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = GuardiaTeal,
    onPrimary = Color(0xFF00251E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFB6F2E7),
    secondary = GuardiaGreen,
    onSecondary = Color(0xFF00210E),
    tertiary = GuardiaAmber,
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = GuardiaRed,
    errorContainer = RedContainer,
    onErrorContainer = OnRedContainer,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA9B4B2),
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColors = lightColorScheme(
    primary = GuardiaTealDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF00201A),
    secondary = GuardiaGreen,
    tertiary = GuardiaAmber,
    onTertiary = Color(0xFF3D2A00),
    error = GuardiaRed,
    errorContainer = OnRedContainer,
    onErrorContainer = RedContainer,
    background = LightBackground,
    onBackground = OnLight,
    surface = LightSurface,
    onSurface = OnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF44524F),
    surfaceContainerLowest = LightContainerLowest,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

/** Brand gradient used on hero surfaces — electric teal easing into a deep cyan-blue. */
val GuardiaHeroGradient = Brush.linearGradient(listOf(Color(0xFF2BF5D6), Color(0xFF12A6C4), Color(0xFF0C6FA8)))
val GuardiaAlertGradient = Brush.linearGradient(listOf(Color(0xFFFF6A5E), Color(0xFFC23021)))

/** Subtle glassy panel gradient for cards/console surfaces (dark theme). */
val GuardiaPanelGradient = Brush.verticalGradient(listOf(Color(0xFF132125), Color(0xFF0A1215)))

@Composable
fun GuardiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GuardiaTypography,
        shapes = GuardiaShapes,
        content = content,
    )
}

package com.guardia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The full token set for the current theme, read as `Guardia.colors.brand`.
 *
 * It carries three groups: shadcn's structural slots (background / card / muted / border / ring),
 * which Material 3 has no exact equivalent for; the semantic hues; and the *expressive* tokens —
 * gradients, glows and the aurora palette — which is where the app's character lives. Those last
 * ones are listed as colour stops rather than `Brush`es so a caller can build a linear, radial or
 * sweep gradient from the same ramp without the theme deciding the geometry for it.
 */
@Immutable
data class GuardiaColors(
    // --- structure ---
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardForeground: Color,
    val popover: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val border: Color,
    /** The lit top edge of a card's border — what makes a surface read as glass rather than paint. */
    val borderHighlight: Color,
    val input: Color,
    val ring: Color,
    // --- brand ---
    /** Signal Cyan: the system is watching, or this is the thing to press. Nothing else. */
    val brand: Color,
    val brandForeground: Color,
    /** Tinted fill for brand chips and icon tiles. */
    val brandSubtle: Color,
    /** Brand at glow strength — for shadows and halos, always used at low alpha. */
    val brandGlow: Color,
    // --- semantic ---
    val destructive: Color,
    val destructiveForeground: Color,
    val destructiveSubtle: Color,
    val success: Color,
    val successForeground: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningForeground: Color,
    val warningSubtle: Color,
    val info: Color,
    val infoForeground: Color,
    val infoSubtle: Color,
    val violet: Color,
    val violetSubtle: Color,
    // --- expressive ---
    /** Cyan → aqua → deep blue. The hero ramp: primary buttons, the live gauge, brand marks. */
    val gradientBrand: List<Color>,
    val gradientDanger: List<Color>,
    val gradientSuccess: List<Color>,
    /** Two-stop fill for a card, top to bottom. */
    val gradientCard: List<Color>,
    /** The three drifting blobs behind the whole app. Already at their display alpha. */
    val aurora: List<Color>,
    val chart: List<Color>,
    val isDark: Boolean,
)

private val LightGuardiaColors = GuardiaColors(
    background = LightBackground,
    foreground = LightForeground,
    card = LightCard,
    cardTop = LightCardTop,
    cardBottom = LightCardBottom,
    cardForeground = LightCardForeground,
    popover = LightPopover,
    primary = LightPrimary,
    primaryForeground = LightPrimaryForeground,
    secondary = LightSecondary,
    secondaryForeground = LightSecondaryForeground,
    muted = LightMuted,
    mutedForeground = LightMutedForeground,
    accent = LightAccent,
    accentForeground = LightAccentForeground,
    border = LightBorder,
    borderHighlight = LightBorderHighlight,
    input = LightInput,
    ring = LightRing,
    brand = Cyan600,
    brandForeground = Color(0xFFFFFFFF),
    brandSubtle = Cyan50,
    brandGlow = Cyan500,
    destructive = Rose600,
    destructiveForeground = Rose600,
    destructiveSubtle = Rose50,
    success = Emerald600,
    successForeground = Emerald600,
    successSubtle = Emerald50,
    warning = Amber600,
    warningForeground = Amber600,
    warningSubtle = Amber50,
    info = Blue600,
    infoForeground = Blue600,
    infoSubtle = Blue50,
    violet = Violet600,
    violetSubtle = Color(0xFFF5F3FF),
    gradientBrand = listOf(Cyan400, Cyan600, DeepOcean),
    gradientDanger = listOf(Rose500, Rose600),
    gradientSuccess = listOf(Emerald400, Emerald600),
    gradientCard = listOf(LightCardTop, LightCardBottom),
    // Light mode gets a whisper of colour; any more and white cards stop reading as white.
    aurora = listOf(
        Cyan400.copy(alpha = 0.16f),
        Violet400.copy(alpha = 0.10f),
        Blue400.copy(alpha = 0.10f),
    ),
    chart = ChartLight,
    isDark = false,
)

private val DarkGuardiaColors = GuardiaColors(
    background = DarkBackground,
    foreground = DarkForeground,
    card = DarkCard,
    cardTop = DarkCardTop,
    cardBottom = DarkCardBottom,
    cardForeground = DarkCardForeground,
    popover = DarkPopover,
    primary = DarkPrimary,
    primaryForeground = DarkPrimaryForeground,
    secondary = DarkSecondary,
    secondaryForeground = DarkSecondaryForeground,
    muted = DarkMuted,
    mutedForeground = DarkMutedForeground,
    accent = DarkAccent,
    accentForeground = DarkAccentForeground,
    border = DarkBorder,
    borderHighlight = DarkBorderHighlight,
    input = DarkInput,
    ring = DarkRing,
    brand = SignalCyan,
    brandForeground = Color(0xFF00201C),
    brandSubtle = Color(0xFF0E2C2E),
    brandGlow = SignalCyan,
    destructive = Rose500,
    destructiveForeground = Rose300,
    destructiveSubtle = Color(0xFF2B1017),
    success = Emerald400,
    successForeground = Emerald300,
    successSubtle = Color(0xFF06251C),
    warning = Amber400,
    warningForeground = Amber300,
    warningSubtle = Color(0xFF2C1E06),
    info = Blue400,
    infoForeground = Blue300,
    infoSubtle = Color(0xFF10203C),
    violet = Violet400,
    violetSubtle = Color(0xFF1C1633),
    gradientBrand = listOf(SignalCyan, DeepAqua, DeepOcean),
    gradientDanger = listOf(Rose400, Rose600),
    gradientSuccess = listOf(Emerald300, Emerald600),
    gradientCard = listOf(DarkCardTop, DarkCardBottom),
    aurora = listOf(
        SignalCyan.copy(alpha = 0.22f),
        Violet500.copy(alpha = 0.16f),
        DeepOcean.copy(alpha = 0.20f),
    ),
    chart = ChartDark,
    isDark = true,
)

val LocalGuardiaColors = staticCompositionLocalOf { DarkGuardiaColors }

object Guardia {
    val colors: GuardiaColors
        @Composable @ReadOnlyComposable get() = LocalGuardiaColors.current
}

// Material 3 roles are mapped onto these slots so stock Material components (Slider, TopAppBar,
// AlertDialog, …) land on the right colours without being individually restyled.
private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkPrimaryForeground,
    primaryContainer = Color(0xFF0E2C2E),
    onPrimaryContainer = Cyan200,
    inversePrimary = Cyan600,
    secondary = Cyan300,
    onSecondary = DarkPrimaryForeground,
    secondaryContainer = DarkSecondary,
    onSecondaryContainer = DarkSecondaryForeground,
    tertiary = Amber400,
    onTertiary = Neutral950,
    tertiaryContainer = Color(0xFF2C1E06),
    onTertiaryContainer = Amber300,
    error = Rose500,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2B1017),
    onErrorContainer = Rose300,
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkCard,
    onSurface = DarkCardForeground,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkMutedForeground,
    surfaceTint = Color.Transparent,
    inverseSurface = Neutral50,
    inverseOnSurface = Neutral950,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkMuted,
    surfaceContainerHighest = DarkSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    scrim = Color(0xCC000308),
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightPrimaryForeground,
    primaryContainer = Cyan50,
    onPrimaryContainer = Cyan800,
    inversePrimary = Cyan300,
    secondary = Cyan700,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSecondary,
    onSecondaryContainer = LightSecondaryForeground,
    tertiary = Amber600,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Amber50,
    onTertiaryContainer = Color(0xFF422006),
    error = Rose600,
    onError = Color(0xFFFFFFFF),
    errorContainer = Rose50,
    onErrorContainer = Color(0xFF4C0519),
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightCard,
    onSurface = LightCardForeground,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightMutedForeground,
    surfaceTint = Color.Transparent,
    inverseSurface = Neutral900,
    inverseOnSurface = Neutral50,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LightCard,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightMuted,
    surfaceContainerHighest = LightSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    scrim = Color(0x99000308),
)

/**
 * Brand gradient for hero marks. Kept as a top-level `Brush` because the few callers that use it
 * (the lock mark, app-lock and stop-guard discs) draw a fixed-size circle where the geometry never
 * varies; anything that needs to size the ramp to its own bounds should build one from
 * [GuardiaColors.gradientBrand] instead.
 */
val GuardiaHeroGradient = Brush.linearGradient(listOf(SignalCyan, DeepAqua, DeepOcean))

@Composable
fun GuardiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val guardiaColors = if (darkTheme) DarkGuardiaColors else LightGuardiaColors
    CompositionLocalProvider(LocalGuardiaColors provides guardiaColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = GuardiaTypography,
            shapes = GuardiaShapes,
            content = content,
        )
    }
}

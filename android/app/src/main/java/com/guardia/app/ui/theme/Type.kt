@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.guardia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guardia.app.R

/**
 * Guardia type — a Compose port of shadcn/ui's type scale.
 *
 * shadcn does almost nothing exotic with type, and that restraint is the point: one UI sans at
 * Tailwind's step sizes, `font-semibold tracking-tight` for headings, `text-sm` as the *default*
 * body size (14sp, not 16 — this is what makes shadcn interfaces read as dense and tidy), and
 * `text-muted-foreground` doing the work that a second font would otherwise do.
 *
 * Two deliberate keeps from the previous system:
 *  - [GuardiaMono] (JetBrains Mono) for numbers and machine states, so readouts stay column-aligned
 *    — shadcn's `font-mono`/`tabular-nums`.
 *  - [SpaceGrotesk] stays declared for the wordmark, which is brand rather than interface.
 *
 * Both bundled fonts are licensed under the SIL Open Font License 1.1 (see THIRD_PARTY_NOTICES.md).
 */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val GuardiaMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
    Font(R.font.jetbrains_mono_medium, weight = FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, weight = FontWeight.Bold),
)

/** The UI face. shadcn ships `font-sans` = the platform UI stack; on Android that's Roboto. */
private val Sans = FontFamily.Default

private const val TIGHT = -0.4f   // Tailwind tracking-tight (-0.025em) at heading sizes
private const val TIGHTER = -0.6f // tracking-tighter, for the two display steps

val GuardiaTypography = Typography(
    // text-4xl / text-3xl — page-defining numbers and hero counts only.
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = TIGHTER.sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = TIGHTER.sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = TIGHT.sp),

    // Headings — shadcn's h1/h2/h3: semibold, tight tracking, generous line height.
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = TIGHT.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = TIGHT.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = TIGHT.sp),

    // Titles — CardTitle is text-base/text-lg semibold; list rows are text-sm medium.
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = TIGHT.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),

    // Body — text-sm is the default, exactly as in shadcn.
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),

    // Labels — button text is text-sm font-medium; the small step is for badges and captions.
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * Section label above a group of cards. shadcn writes these as
 * `text-sm font-medium text-muted-foreground` — sentence case, no tracking games. The old system
 * shouted these in wide-tracked uppercase mono; this is the quiet equivalent.
 */
val OverlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

/** Machine state in a badge — `font-mono text-xs`. Uppercase is applied by the caller. */
val StatusReadout = TextStyle(
    fontFamily = GuardiaMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.6.sp,
)

/** Large metric value — `font-mono text-2xl font-semibold tabular-nums`. */
val DataDisplay = TextStyle(
    fontFamily = GuardiaMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.8).sp,
)

/** Inline mono for timestamps, ids and similarity percentages inside body copy. */
val MonoCaption = TextStyle(
    fontFamily = GuardiaMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

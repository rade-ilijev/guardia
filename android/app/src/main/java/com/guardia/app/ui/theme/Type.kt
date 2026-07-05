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
 * Guardia type system — the app's visual voice.
 *
 * - **Space Grotesk** (variable): display, headlines, titles. Geometric and slightly technical —
 *   the "product" voice.
 * - **JetBrains Mono**: status readouts, overlines, stat values, timestamps. The "console" voice —
 *   it makes numbers align and security states read like instrumentation.
 * - Platform sans (Roboto): body copy, where readability beats personality.
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

private val base = Typography()

val GuardiaTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = base.displayMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = base.titleMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = base.labelSmall.copy(fontFamily = GuardiaMono, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
)

/** Console-style section overline: mono, uppercase, widely tracked. */
val OverlineStyle = TextStyle(
    fontFamily = GuardiaMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 2.sp,
)

/** Mono style for status readouts (state chips, live values). */
val StatusReadout = TextStyle(
    fontFamily = GuardiaMono,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = 1.5.sp,
)

/** Mono style for large data values (stat tiles, percentages, timers). */
val DataDisplay = TextStyle(
    fontFamily = GuardiaMono,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    letterSpacing = (-0.5).sp,
)

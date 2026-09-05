package com.guardia.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Guardia palette.
 *
 * The structure is still shadcn's — a neutral ramp carrying background / card / muted / border, so
 * the interface stays calm and legible — but the app has an identity again: **Signal Cyan**, the
 * colour of the thing being alive. It appears on the primary action, on "protected", on the status
 * gauge and in the ambient light behind the page, and nowhere else. Everything that isn't the
 * system watching or the user acting stays on the neutral ramp.
 *
 * The neutral ramp is deliberately *not* pure gray: it carries a few degrees of cyan so that brand
 * light bleeding across a surface looks like it belongs there rather than like a coloured film laid
 * over gray. In dark mode the difference is the gap between "an app with a neon accent" and "an
 * instrument lit from inside".
 */

// ---------------------------------------------------------------------------------------------
// Brand — Signal Cyan.
// ---------------------------------------------------------------------------------------------
val Cyan50 = Color(0xFFECFEFB)
val Cyan100 = Color(0xFFCFFAF2)
val Cyan200 = Color(0xFFA0F3E7)
val Cyan300 = Color(0xFF5FE6D6)
val Cyan400 = Color(0xFF2DD4BF)
val Cyan500 = Color(0xFF14B8A6)
val Cyan600 = Color(0xFF0D9488)
val Cyan700 = Color(0xFF0F766E)
val Cyan800 = Color(0xFF115E59)
val Cyan900 = Color(0xFF134E4A)
val Cyan950 = Color(0xFF042F2E)

/** The accent at full voltage — used for glows, the live ring, and the top of brand gradients. */
val SignalCyan = Color(0xFF2CF5D8)
/** The deep end of the brand gradient; reads as blue rather than green so the ramp has travel. */
val DeepAqua = Color(0xFF0BA5C4)
val DeepOcean = Color(0xFF0B6BA8)

// ---------------------------------------------------------------------------------------------
// Neutral ramp — cyan-tinted, so brand light sits naturally on it.
// ---------------------------------------------------------------------------------------------
val Neutral50 = Color(0xFFF7FAFA)
val Neutral100 = Color(0xFFEFF4F4)
val Neutral200 = Color(0xFFDFE7E7)
val Neutral300 = Color(0xFFC3D0D0)
val Neutral400 = Color(0xFF8A9C9C)
val Neutral500 = Color(0xFF627373)
/** Secondary text in light mode. 500 clears 4.5:1 on the page but only 4.48 on `muted`. */
val Neutral550 = Color(0xFF5A6A6A)
val Neutral600 = Color(0xFF465656)
val Neutral700 = Color(0xFF2C3B3C)
val Neutral800 = Color(0xFF1B2729)
val Neutral900 = Color(0xFF111A1C)
val Neutral950 = Color(0xFF080F11)

// ---------------------------------------------------------------------------------------------
// Light theme
// ---------------------------------------------------------------------------------------------
val LightBackground = Color(0xFFF6FAFA)      // a hair off white, so white cards lift off it
val LightForeground = Color(0xFF0A1214)
val LightCard = Color(0xFFFFFFFF)
val LightCardTop = Color(0xFFFFFFFF)         // top of the card's fill gradient
val LightCardBottom = Color(0xFFFAFDFD)      // bottom — barely there, but it catches the light
val LightCardForeground = Color(0xFF0A1214)
val LightPopover = Color(0xFFFFFFFF)
val LightPrimary = Cyan700          // 600 put white button labels at 3.7:1
val LightPrimaryForeground = Color(0xFFFFFFFF)
val LightSecondary = Neutral100
val LightSecondaryForeground = Neutral900
val LightMuted = Neutral100
val LightMutedForeground = Neutral550
val LightAccent = Cyan50
val LightAccentForeground = Cyan700
val LightBorder = Neutral200
val LightBorderHighlight = Color(0xFFFFFFFF)
val LightInput = Neutral200
val LightRing = Cyan600             // focus ring needs 3:1 against the page (1.4.11)

// ---------------------------------------------------------------------------------------------
// Dark theme — obsidian with a cool cast.
// ---------------------------------------------------------------------------------------------
val DarkBackground = Color(0xFF050B0D)
val DarkForeground = Color(0xFFE8F2F1)
val DarkCard = Color(0xFF0C1416)
val DarkCardTop = Color(0xFF131E21)          // top of the card's fill gradient — the lit edge
val DarkCardBottom = Color(0xFF0A1214)       // bottom — where the surface falls away
val DarkCardForeground = Color(0xFFE8F2F1)
val DarkPopover = Color(0xFF111B1E)
val DarkPrimary = Cyan300
val DarkPrimaryForeground = Color(0xFF00201C)
val DarkSecondary = Color(0xFF18262A)
val DarkSecondaryForeground = Color(0xFFE8F2F1)
val DarkMuted = Color(0xFF162124)
val DarkMutedForeground = Color(0xFF8FA3A5)
val DarkAccent = Color(0xFF11292B)
val DarkAccentForeground = Cyan300
val DarkBorder = Color(0xFF1E2E31)
val DarkBorderHighlight = Color(0x1FFFFFFF)  // the 12% white top edge that makes a card look glassy
val DarkInput = Color(0xFF223034)
val DarkRing = Cyan400

// ---------------------------------------------------------------------------------------------
// Semantic hues. Cyan means "the system is live"; these mean everything else.
// ---------------------------------------------------------------------------------------------
val Emerald300 = Color(0xFF6EE7B7)
val Emerald400 = Color(0xFF34D399)
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669)
val Emerald700 = Color(0xFF047857)
val Emerald50 = Color(0xFFECFDF5)

val Amber300 = Color(0xFFFCD34D)
val Amber400 = Color(0xFFFBBF24)
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFD97706)
val Amber700 = Color(0xFFB45309)
val Amber50 = Color(0xFFFFFBEB)

val Rose300 = Color(0xFFFDA4AF)
val Rose400 = Color(0xFFFB7185)
val Rose500 = Color(0xFFF43F5E)
val Rose600 = Color(0xFFE11D48)
val Rose700 = Color(0xFFBE123C)
val Rose800 = Color(0xFF9F1239)
val Rose50 = Color(0xFFFFF1F2)

val Blue300 = Color(0xFF93C5FD)
val Blue400 = Color(0xFF60A5FA)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
val Blue50 = Color(0xFFEFF6FF)

val Violet300 = Color(0xFFC4B5FD)
val Violet400 = Color(0xFFA78BFA)
val Violet500 = Color(0xFF8B5CF6)
val Violet600 = Color(0xFF7C3AED)

// ---------------------------------------------------------------------------------------------
// Chart ramp — brand-led, then around the wheel, so a multi-series chart still reads as Guardia.
// ---------------------------------------------------------------------------------------------
val ChartDark = listOf(Cyan300, Violet400, Amber400, Rose400, Blue400)
val ChartLight = listOf(Cyan600, Violet600, Amber600, Rose600, Blue600)

// ---------------------------------------------------------------------------------------------
// High contrast — opt-in, from Settings › Accessibility.
//
// The default border is a hairline by design (see DESIGN.md): it is a *seam* between surfaces,
// not a drawn edge, which is what keeps a screen of twelve cards from reading as a grid of boxes.
// That costs it contrast — 1.19:1 in light, 1.21:1 in dark — well under the 3:1 that WCAG 1.4.11
// asks of a UI component boundary. Rather than make every screen heavier for everyone, the
// stronger edge is a setting: these clear 3:1 against both the page and a card.
// ---------------------------------------------------------------------------------------------
val LightBorderHC = Color(0xFF7C8E8E)        // 3.27:1 on the page, 3.43:1 on a card
val DarkBorderHC = Neutral500                // 3.98:1 on the page, 3.42:1 on a card
/** In high contrast the lit top edge stops being a suggestion and becomes visible in its own right. */
val DarkBorderHighlightHC = Color(0x66FFFFFF)

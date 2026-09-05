package com.guardia.app

import androidx.compose.ui.graphics.Color
import com.guardia.app.ui.theme.Amber700
import com.guardia.app.ui.theme.Cyan600
import com.guardia.app.ui.theme.Cyan700
import com.guardia.app.ui.theme.Cyan800
import com.guardia.app.ui.theme.DarkBackground
import com.guardia.app.ui.theme.DarkBorderHC
import com.guardia.app.ui.theme.DarkCardTop
import com.guardia.app.ui.theme.DeepAqua
import com.guardia.app.ui.theme.Emerald700
import com.guardia.app.ui.theme.LightBackground
import com.guardia.app.ui.theme.LightBorderHC
import com.guardia.app.ui.theme.LightCard
import com.guardia.app.ui.theme.LightMuted
import com.guardia.app.ui.theme.LightMutedForeground
import com.guardia.app.ui.theme.Rose700
import com.guardia.app.ui.theme.Rose800
import com.guardia.app.ui.theme.SignalCyan
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette's contrast guarantees, as arithmetic.
 *
 * These were all measured failures before they were tests. Light mode shipped white button labels
 * on the bright end of the brand ramp at 1.86:1, and the 600-step semantic colours sat between
 * 3.0 and 4.5:1 on the page — legible to most people, and not to everyone. Colour choices drift
 * when someone is matching a mock rather than checking a ratio, so the ratios live here now.
 *
 * WCAG 2.1: 1.4.3 wants 4.5:1 for body text, 1.4.11 wants 3:1 for a UI component boundary or a
 * focus indicator. Guardia's body text is 14sp, which is not "large text" under any definition,
 * so nothing here gets the 3:1 large-text allowance.
 */
class ContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    /** Relative luminance, WCAG 2.1 definition. */
    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, min: Double) {
        val actual = ratio(fg, bg)
        assertTrue(
            "$label: %.2f:1, needs %.1f:1".format(actual, min),
            actual >= min,
        )
    }

    private val white = Color(0xFFFFFFFF)
    private val darkBrandForeground = Color(0xFF00201C)

    // -- text ---------------------------------------------------------------------------------

    @Test
    fun `light secondary text clears AA on every surface it lands on`() {
        assertContrast("mutedForeground on page", LightMutedForeground, LightBackground, 4.5)
        assertContrast("mutedForeground on card", LightMutedForeground, LightCard, 4.5)
        // The tightest of the three, and the one the previous Neutral500 missed at 4.48.
        assertContrast("mutedForeground on muted", LightMutedForeground, LightMuted, 4.5)
    }

    @Test
    fun `light semantic colours are readable as text on the page`() {
        assertContrast("brand", Cyan700, LightBackground, 4.5)
        assertContrast("success", Emerald700, LightBackground, 4.5)
        assertContrast("warning", Amber700, LightBackground, 4.5)
        assertContrast("destructive", Rose700, LightBackground, 4.5)
    }

    // -- button fills -------------------------------------------------------------------------

    /**
     * A gradient fill has to clear the bar at *every* stop, not on average — the label sits across
     * the whole width. This is the check that the old ramp failed: white on Cyan400 was 1.86:1.
     */
    @Test
    fun `a label on a gradient button clears AA at every stop`() {
        listOf(Cyan700, Cyan800).forEach {
            assertContrast("white on light brand stop", white, it, 4.5)
        }
        listOf(SignalCyan, DeepAqua).forEach {
            assertContrast("dark brand foreground on dark brand stop", darkBrandForeground, it, 4.5)
        }
        listOf(Rose700, Rose800).forEach {
            assertContrast("white on light destructive stop", white, it, 4.5)
        }
    }

    // -- non-text -----------------------------------------------------------------------------

    @Test
    fun `high contrast borders clear the 3 to 1 that 1_4_11 asks of a component edge`() {
        assertContrast("light border on page", LightBorderHC, LightBackground, 3.0)
        assertContrast("light border on card", LightBorderHC, LightCard, 3.0)
        assertContrast("dark border on page", DarkBorderHC, DarkBackground, 3.0)
        assertContrast("dark border on card", DarkBorderHC, DarkCardTop, 3.0)
    }

    @Test
    fun `the light focus ring is findable against the page`() {
        assertContrast("focus ring", Cyan600, LightBackground, 3.0)
    }
}

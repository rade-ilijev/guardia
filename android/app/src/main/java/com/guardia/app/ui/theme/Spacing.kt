package com.guardia.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Tailwind's 4px spacing scale, which is what shadcn's `p-4 / gap-2 / space-y-6` classes resolve to.
 * [screen] is the page gutter (Tailwind `px-4` on mobile), [card] the standard CardContent padding
 * (`p-6`, trimmed to 20dp for phone widths).
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Horizontal page gutter. */
    val screen = 16.dp
    /** Padding inside a card body. */
    val card = 20.dp
    /** Vertical rhythm between stacked sections. */
    val section = 24.dp

    /**
     * Bottom padding a scrolling tab screen needs so its last item clears the floating navigation
     * bar. The bar is 64dp tall with a 12dp inset above and below, and it sits inside the system
     * navigation inset — so anything less than this leaves the final card half-hidden behind glass.
     */
    val bottomBarClearance = 128.dp
}

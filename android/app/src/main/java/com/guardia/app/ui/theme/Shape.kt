package com.guardia.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * shadcn derives every corner from one `--radius: 0.5rem` variable:
 *   rounded-sm = radius-4 -> 4dp, rounded-md = radius-2 -> 6dp,
 *   rounded-lg = radius   -> 8dp, rounded-xl = radius+4 -> 12dp
 *
 * Cards are rounded-xl, buttons/inputs/badges rounded-md, small chips rounded-sm. Nothing in the
 * system is more rounded than 12dp except deliberate pills (CircleShape), which is what keeps a
 * shadcn interface looking crisp rather than soft.
 */
object Radius {
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
    val xl = 12.dp
    val xxl = 16.dp
}

val GuardiaShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.md),
    medium = RoundedCornerShape(Radius.lg),
    large = RoundedCornerShape(Radius.xl),
    extraLarge = RoundedCornerShape(Radius.xxl),
)

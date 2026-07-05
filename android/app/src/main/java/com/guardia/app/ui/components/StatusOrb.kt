package com.guardia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.guardia.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Futuristic status hero: layered radial glow, a radar dial of tick marks, concentric rings, a
 * pulsing "ping" halo and two counter-rotating scan arcs (while [active]), with the brand icon at
 * the center. Conveys "live protection" with calm, deliberate motion rather than busy flicker.
 */
@Composable
fun StatusOrb(
    active: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sweep",
    )
    val counterSweep by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(6800, easing = LinearEasing)),
        label = "counterSweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2200), repeatMode = RepeatMode.Reverse),
        label = "breathe",
    )
    // A single expanding + fading "ping" ring, like sonar (only meaningful while active).
    val ping by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ping",
    )
    val live by animateColorAsState(
        targetValue = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(500),
        label = "live",
    )
    val glowAlpha = if (active) breathe * 0.55f else 0.12f
    val ringBase = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f

            // Layered radial glow for depth (bright core → soft falloff).
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        live.copy(alpha = glowAlpha),
                        live.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )

            // Expanding sonar ping (active only): grows outward and fades.
            if (active) {
                val pingR = r * (0.5f + ping * 0.5f)
                drawCircle(
                    color = live.copy(alpha = (1f - ping) * 0.35f),
                    radius = pingR,
                    center = c,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            // Radar dial: short tick marks around the outer edge.
            val tickCount = 48
            val tickOuter = r * 0.99f
            val tickInner = r * 0.93f
            for (i in 0 until tickCount) {
                val ang = (i.toFloat() / tickCount) * 2f * Math.PI.toFloat()
                val major = i % 4 == 0
                val innerR = if (major) r * 0.90f else tickInner
                val start = Offset(c.x + cos(ang) * innerR, c.y + sin(ang) * innerR)
                val end = Offset(c.x + cos(ang) * tickOuter, c.y + sin(ang) * tickOuter)
                drawLine(
                    color = ringBase.copy(alpha = if (major) 0.55f else 0.28f),
                    start = start,
                    end = end,
                    strokeWidth = (if (major) 1.6f else 1.0f).dp.toPx(),
                )
            }

            // Concentric rings.
            val rings = listOf(0.86f, 0.68f, 0.5f)
            rings.forEachIndexed { i, frac ->
                drawCircle(
                    color = ringBase.copy(alpha = 0.5f - i * 0.12f),
                    radius = r * frac,
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            // Bright base arc on the outer ring.
            val arcR = r * 0.86f
            val arcSize = Size(arcR * 2, arcR * 2)
            val topLeft = Offset(c.x - arcR, c.y - arcR)
            drawArc(
                color = live.copy(alpha = if (active) 0.85f else 0.4f),
                startAngle = -90f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )

            // Two counter-rotating scan arcs (only while active) for a "live sweep" feel.
            if (active) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, live), center = c),
                    startAngle = sweep,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.68f, c.y - r * 0.68f),
                    size = Size(r * 0.68f * 2, r * 0.68f * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, live.copy(alpha = 0.6f)), center = c),
                    startAngle = counterSweep,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.5f, c.y - r * 0.5f),
                    size = Size(r * 0.5f * 2, r * 0.5f * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                // A small orbiting node riding the outer ring.
                rotate(degrees = sweep + 90f, pivot = c) {
                    drawCircle(
                        color = live,
                        radius = 3.dp.toPx(),
                        center = Offset(c.x, c.y - r * 0.86f),
                    )
                }
            }
        }
        Icon(
            icon,
            contentDescription = null,
            tint = live,
            modifier = Modifier.size(size * 0.30f),
        )
    }
}

/** Brand mark: luminous G with shield nested inside (from [R.drawable.ic_guardia_logo]). */
@Composable
fun GuardiaLogo(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    Image(
        painter = painterResource(R.drawable.ic_guardia_logo),
        contentDescription = "Guardia",
        modifier = modifier.size(size),
    )
}

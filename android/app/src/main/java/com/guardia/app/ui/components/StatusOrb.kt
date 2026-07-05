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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Futuristic status hero: concentric rings, a soft radial glow and (when [active]) a slowly
 * sweeping scan arc, with an icon at the center. Conveys "live protection" without busy motion.
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
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2200), repeatMode = RepeatMode.Reverse),
        label = "breathe",
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

            // Soft outer glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(live.copy(alpha = glowAlpha), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )

            // Concentric rings.
            val rings = listOf(0.92f, 0.74f, 0.56f)
            rings.forEachIndexed { i, frac ->
                drawCircle(
                    color = ringBase.copy(alpha = 0.5f - i * 0.12f),
                    radius = r * frac,
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            // Bright base arc on the outer ring.
            val arcR = r * 0.92f
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

            // Sweeping scan arc (only while active).
            if (active) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, live), center = c),
                    startAngle = sweep,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.74f, c.y - r * 0.74f),
                    size = Size(r * 0.74f * 2, r * 0.74f * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
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

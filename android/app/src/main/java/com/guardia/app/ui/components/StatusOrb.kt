package com.guardia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guardia.app.R
import com.guardia.app.ui.theme.Guardia

/**
 * The dashboard's status hero — a gauge that is unmistakably *live* when the guard is running.
 *
 * It is built in four layers, because that is what separates something that looks alive from
 * something that merely blinks:
 *
 *  1. a hairline track ring, always there, so the gauge has a shape even when off;
 *  2. an indicator arc filled with the brand ramp, which sweeps round as the guard arms;
 *  3. two sonar rings expanding out of the centre on an offset cycle — the visual of a *check
 *     happening*, and the only continuously moving element on the page;
 *  4. a rotating comet riding the ring, which is what makes the eye read the gauge as scanning
 *     rather than as a static dial with a glow on it.
 *
 * Layers 3 and 4 live in [LiveLayer], composed only while the guard is actually running and motion
 * is allowed — so an idle or reduced-motion device starts no animation clock at all and the static
 * gauge underneath still says everything it needs to.
 */
@Composable
fun StatusOrb(
    active: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val c = Guardia.colors
    val statusColor by animateColorAsState(
        targetValue = if (active) accent else c.mutedForeground,
        animationSpec = tween(420),
        label = "orbStatus",
    )
    // The arc fills as the guard arms and retracts when it stops — state feedback, so it still runs
    // under reduced motion (it settles and then stops, which is the distinction that matters).
    val fill by animateFloatAsState(
        targetValue = if (active) 1f else 0.08f,
        animationSpec = tween(760),
        label = "orbFill",
    )
    val reduced = rememberReducedMotion()
    val ramp = remember(statusColor, active, c.gradientBrand) {
        if (active) c.gradientBrand else listOf(statusColor, statusColor)
    }

    // The gauge is a picture of a state that is spelled out in words directly beside it. Left as
    // it was, a screen reader walked its inner nodes and then read the same state again from the
    // label; collapsing it to nothing makes the written state the single source of that
    // announcement. The dashboard marks that text as a live region so a change is spoken.
    Box(
        modifier = modifier.size(size).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val stroke = 7.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(centre.x - radius, centre.y - radius)

            // Track — the full circle at a constant, quiet weight.
            drawArc(
                color = c.muted,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Indicator — starts at 12 o'clock, like every gauge the user has ever read. Drawn with
            // a sweep gradient so the ramp travels *around* the arc rather than across its bounding
            // box, which is the difference between a coloured ring and a lit one.
            drawArc(
                brush = Brush.sweepGradient(
                    colors = ramp + ramp.first(),
                    center = centre,
                ),
                startAngle = -90f, sweepAngle = 360f * fill, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        if (active && !reduced) LiveLayer(color = statusColor)

        // Center disc: muted when off, a lit well of brand colour when live.
        val discFill = remember(active, statusColor, c.muted) {
            if (active) {
                Brush.verticalGradient(
                    listOf(statusColor.copy(alpha = 0.24f), statusColor.copy(alpha = 0.05f)),
                )
            } else {
                Brush.verticalGradient(listOf(c.muted, c.muted))
            }
        }
        Box(
            modifier = Modifier
                .then(
                    if (active) Modifier.glow(statusColor, CircleShape, radius = 32.dp, alpha = 0.55f)
                    else Modifier,
                )
                .size(size * 0.46f)
                .clip(CircleShape)
                .background(discFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(size * 0.21f),
            )
        }
    }
}

/**
 * The moving parts: two sonar rings and a comet on the ring.
 *
 * The two rings share one clock and are drawn half a cycle apart, so there is always one expanding
 * — a single ring leaves a dead beat between pulses that reads as the app having stopped.
 */
@Composable
private fun LiveLayer(color: Color) {
    val transition = rememberInfiniteTransition(label = "orbLive")
    val ping by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "orbPing",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "orbSweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.05f, targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(2200), repeatMode = RepeatMode.Reverse),
        label = "orbBreathe",
    )

    Canvas(Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f - 7.dp.toPx()

        // Interior wash, breathing — stops the disc reading as a hole while the guard is running.
        drawCircle(color = color.copy(alpha = breathe), radius = maxR * 0.92f, center = centre)

        // Two sonar rings, half a cycle apart.
        for (offset in listOf(0f, 0.5f)) {
            val t = (ping + offset) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - t) * 0.30f),
                radius = maxR * (0.34f + t * 0.66f),
                center = centre,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // The comet: a short, bright arc riding the ring, fading out behind itself.
        rotate(degrees = sweep, pivot = centre) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = 0.9f)),
                    center = centre,
                ),
                startAngle = -110f,
                sweepAngle = 74f,
                useCenter = false,
                topLeft = Offset(centre.x - maxR, centre.y - maxR),
                size = Size(maxR * 2, maxR * 2),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** Brand mark: the shield sentinel (from [R.drawable.ic_guardia_logo]). */
@Composable
fun GuardiaLogo(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    Image(
        painter = painterResource(R.drawable.ic_guardia_logo),
        contentDescription = "Guardia",
        modifier = modifier.size(size),
    )
}

/**
 * The full brand lockup: the mark beside the wordmark, the wordmark carrying the brand ramp.
 *
 * The gradient is the app's own [com.guardia.app.ui.theme.GuardiaColors.gradientBrandAction] — the
 * stops trimmed for contrast — rather than the decorative ramp, because a wordmark is still text
 * and its every stop clears AA on the page behind it (14.3:1 and 6.8:1 in dark, 5.2:1 and 7.2:1 in
 * light). Brand colour on the one string that is allowed to be pure brand.
 *
 * Exposed as one node reading "Guardia": the mark and the letters are the same thing said twice.
 */
@Composable
fun GuardiaLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 36.dp,
) {
    val c = com.guardia.app.ui.theme.Guardia.colors
    val ramp = remember(c.gradientBrandAction) {
        Brush.linearGradient(c.gradientBrandAction)
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "Guardia" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GuardiaLogo(size = markSize)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "GUARDIA",
            style = com.guardia.app.ui.theme.WordmarkStyle.copy(brush = ramp),
        )
    }
}

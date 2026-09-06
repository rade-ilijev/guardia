package com.guardia.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.Guardia
import kotlinx.coroutines.delay

/*
 * ---------------------------------------------------------------------------------------------
 * The motion layer.
 *
 * Every animation here is gated on [rememberReducedMotion], which reads the system's animator
 * duration scale. When the user has turned animations off — an accessibility preference as often
 * as a battery one — the ambient pieces are not merely paused, they are never composed, so no
 * animation clock is started and nothing recomposes on a frame callback.
 * ---------------------------------------------------------------------------------------------
 */

/**
 * The app's own animation preference, provided at the root: null follows the system animator
 * setting, true forces ambient motion on, false forces it off. See
 * [com.guardia.app.data.AppPreferences.animationsMode].
 */
val LocalMotionOverride = androidx.compose.runtime.compositionLocalOf<Boolean?> { null }

/**
 * True when ambient animation should not run.
 *
 * The in-app preference wins when set; otherwise this reads the system animator duration scale,
 * which is both an accessibility preference and a battery choice. Ambient/decorative animation
 * (drifting glows, pulses) must not run then; state-change feedback can still snap instantly.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    // Read unconditionally so the branch below never moves a `remember` in or out of composition.
    val systemReduced = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    return when (LocalMotionOverride.current) {
        true -> false      // the user asked for motion, whatever the system says
        false -> true      // the user turned it off in Guardia
        null -> systemReduced
    }
}

/**
 * The ambient light behind the whole app: three wide, soft colour fields drifting slowly against
 * the page background.
 *
 * Each blob is drawn through `drawWithCache`, which keeps the `Brush` and the centre offset alive
 * between frames and only rebuilds them when the values they depend on actually change. That
 * matters because the drifts below run for as long as the app is open: rebuilding three radial
 * gradients on every one of sixty frames a second is the kind of steady, invisible garbage that
 * turns up later as stutter on a mid-range phone. The periods are long enough (19–31 seconds) that
 * consecutive frames rarely change the cached values at all.
 *
 * Under reduced motion the same three blobs are drawn once, in their mid positions: the page keeps
 * its depth and colour, it just stops moving.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier) {
    val c = Guardia.colors
    val reduced = rememberReducedMotion()

    Box(modifier.fillMaxSize().background(c.background)) {
        if (reduced) {
            val still = remember { mutableStateOf(0.5f) }
            AuroraBlob(c.aurora[0], still, -0.20f, 0.02f, -0.28f, -0.06f, 1.75f)
            AuroraBlob(c.aurora[1], still, 0.34f, 0.62f, 0.26f, 0.52f, 1.45f)
            AuroraBlob(c.aurora[2], still, -0.18f, 0.06f, 1.10f, 1.42f, 1.60f)
            return@Box
        }
        val transition = rememberInfiniteTransition(label = "aurora")
        // Three drifts at deliberately unrelated periods (23s / 31s / 19s) so the composition never
        // visibly repeats — with matching periods the eye picks up the loop within a minute.
        val a = transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "auroraA",
        )
        val b = transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(31_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "auroraB",
        )
        val d = transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "auroraD",
        )
        AuroraBlob(c.aurora[0], a, -0.20f, 0.02f, -0.28f, -0.06f, 1.75f)
        AuroraBlob(c.aurora[1], b, 0.34f, 0.62f, 0.26f, 0.52f, 1.45f)
        AuroraBlob(c.aurora[2], d, -0.18f, 0.06f, 1.10f, 1.42f, 1.60f)
    }
}

/**
 * One blob: a soft radial field that drifts from ([x0], [y0]) to ([x1], [y1]) — both in multiples
 * of the screen width, measured from the parent's top-left — while breathing slightly.
 *
 * The brush is remembered against the colour alone, and the drift is applied inside a
 * [graphicsLayer] *block*. That block reads [progress] during the layer-update phase rather than
 * during composition, so an animation that runs for the whole life of the app never recomposes
 * anything and never rebuilds a gradient: each frame is a matrix update the GPU applies to an
 * already-rasterised layer.
 *
 * [sizeFactor] is applied through the layer's scale rather than by sizing the Box, because
 * `fillMaxWidth` coerces its fraction to the incoming constraints — asking for 1.15 of the screen
 * silently gives you exactly the screen, and the blob loses the oversize that makes its edges fall
 * off softly instead of ending at the bezel.
 */
@Composable
private fun AuroraBlob(
    color: Color,
    progress: State<Float>,
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    sizeFactor: Float,
) {
    val brush = remember(color) { Brush.radialGradient(listOf(color, Color.Transparent)) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                val p = progress.value
                translationX = (x0 + (x1 - x0) * p) * size.width
                translationY = (y0 + (y1 - y0) * p) * size.width
                val s = sizeFactor * (0.88f + p * 0.24f)
                scaleX = s
                scaleY = s
            }
            .background(brush),
    )
}

/**
 * A coloured halo behind an element. Uses the platform's tinted shadow, which is GPU-drawn and free
 * compared with painting a blurred layer ourselves.
 *
 * Shadow tinting landed in API 28; on 26–27 this degrades to a normal dark shadow, which still
 * reads as depth — the glow is a flourish, never the thing carrying meaning.
 */
fun Modifier.glow(
    color: Color,
    shape: Shape,
    radius: Dp = 18.dp,
    alpha: Float = 0.45f,
): Modifier = this.shadow(
    elevation = radius,
    shape = shape,
    clip = false,
    ambientColor = color.copy(alpha = alpha),
    spotColor = color.copy(alpha = alpha),
)

/**
 * Staggered entrance: fade up from slightly below, [index] * [stepMillis] after the screen appears.
 *
 * A list whose items arrive together reads as a single flat slab; the same list arriving 40ms apart
 * reads as depth and gives the eye an order to follow. Kept short on purpose — past about the sixth
 * item the delay is capped so a long list never feels like it is loading.
 *
 * [enabled] exists for lazy lists. An item scrolled back into view is a *fresh* composition, so
 * without a guard the whole page would re-animate every time the user scrolled up — callers pass
 * false once the screen has settled, which keeps the entrance to the moment of arrival.
 */
@Composable
fun Modifier.animateEntrance(
    index: Int = 0,
    enabled: Boolean = true,
    stepMillis: Int = 30,
    // The dashboard has more than seven cards. Clamping at seven meant everything past the middle
    // of the page arrived in the same frame, which reads as a jump rather than a cascade — but 45ms
    // x 14 put the last card a full second after the unlock, and a cascade the user is *waiting
    // out* stops reading as polish. 30ms x 10 keeps the staircase and lands the last card in
    // 300ms, which is under the threshold where a transition starts to feel like a delay.
    maxSteps: Int = 10,
    slide: Dp = 14.dp,
): Modifier {
    if (!enabled || rememberReducedMotion()) return this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(maxSteps) * stepMillis).toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = EaseOutCubic),
        label = "entrance",
    )
    val slidePx = with(LocalDensity.current) { slide.toPx() }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * slidePx
    }
}

/**
 * A slow shimmer sweeping across a surface, for loading placeholders. The highlight travels as a
 * horizontal gradient whose stops move, which reads as light passing over the shape rather than the
 * shape itself blinking.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    if (rememberReducedMotion()) return this
    val highlight = Guardia.colors.foreground.copy(alpha = 0.06f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "shimmerX",
    )
    return this.drawWithCache {
        val w = size.width
        val brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, highlight, Color.Transparent),
            start = Offset(w * (x - 0.4f), 0f),
            end = Offset(w * (x + 0.4f), size.height),
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }
}

/**
 * Counts from the previously shown number to [value] instead of swapping it.
 *
 * Every number on the dashboard is a *count of things that happened*, and watching one climb tells
 * the user it changed without needing a badge or a colour flash to say so.
 */
@Composable
fun animatedCount(value: Int, durationMillis: Int = 650): Int {
    if (rememberReducedMotion()) return value
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis, easing = EaseOutCubic),
        label = "count",
    )
    return animated.toInt()
}

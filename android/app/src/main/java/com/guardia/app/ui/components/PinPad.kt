package com.guardia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.GuardiaMono

/**
 * PIN progress: a filled dot per entered digit, a hollow ring per remaining position.
 *
 * The previous version put a radial glow halo behind every filled dot. shadcn's input-OTP shows
 * plain filled slots, and the glow was doing nothing that the fill wasn't already doing — so the
 * dot springs into place and that is the whole animation.
 */
@Composable
fun PinDots(length: Int, max: Int = 6, modifier: Modifier = Modifier) {
    val c = Guardia.colors
    Row(
        // Six identical dots tell a screen-reader user nothing, and without them there is no way to
        // know whether a key press registered — the difference between using the lock screen and
        // guessing at it. Announced as a count and never as digits: the PIN itself must not be
        // spoken aloud, and a device that is being unlocked is by definition in someone's hand and
        // possibly not the owner's.
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "PIN"
            liveRegion = LiveRegionMode.Polite
            stateDescription = "$length of $max digits entered"
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(max) { index ->
            val filled = index < length
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.8f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
                label = "dotScale",
            )
            val color by animateColorAsState(
                targetValue = if (filled) c.foreground else c.border,
                label = "dotColor",
            )
            Box(Modifier.size(13.dp), contentAlignment = Alignment.Center) {
                if (filled) {
                    Box(Modifier.size(11.dp).scale(scale).clip(CircleShape).background(color))
                } else {
                    Box(Modifier.size(11.dp).clip(CircleShape).border(1.5.dp, color, CircleShape))
                }
            }
        }
    }
}

/** On-screen numeric keypad. Calls [onDigit] and [onBackspace]. Dimmed and inert when [enabled] is false. */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    fun tap() = view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> KeypadKey(label = digit.toString(), enabled = enabled) { tap(); onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // The empty bottom-left slot is layout, not a control.
            Spacer(Modifier.size(72.dp).clearAndSetSemantics { })
            KeypadKey(label = "0", enabled = enabled) { tap(); onDigit(0) }
            KeypadKey(enabled = enabled, onClick = { tap(); onBackspace() }) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = Guardia.colors.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    KeypadKey(enabled = enabled, onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GuardiaMono),
            color = Guardia.colors.foreground,
        )
    }
}

/**
 * One key: a bordered circle that fills with `bg-accent` on press — the shadcn ghost-button
 * treatment at keypad scale. No gradient, no accent glow; the press-scale and the fill are the
 * whole feedback.
 */
@Composable
private fun KeypadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = Guardia.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "keyScale",
    )
    val fill by animateColorAsState(
        targetValue = if (pressed) c.accent else c.card,
        label = "keyFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (pressed) c.foreground.copy(alpha = 0.3f) else c.border,
        label = "keyBorder",
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

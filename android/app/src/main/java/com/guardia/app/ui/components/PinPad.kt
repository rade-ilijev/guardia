package com.guardia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.GuardiaMono

/** PIN progress indicator: filled dots spring up and glow; empty positions are hollow rings. */
@Composable
fun PinDots(length: Int, max: Int = 6, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(max) { index ->
            val filled = index < length
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.78f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
                label = "dotScale",
            )
            val color by animateColorAsState(
                targetValue = if (filled) primary else empty.copy(alpha = 0.5f),
                label = "dotColor",
            )
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (filled) {
                    // Soft glow halo behind a filled dot.
                    Box(
                        Modifier
                            .size(14.dp)
                            .scale(scale)
                            .background(
                                Brush.radialGradient(listOf(primary.copy(alpha = 0.45f), androidx.compose.ui.graphics.Color.Transparent)),
                                CircleShape,
                            ),
                    )
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                row.forEach { digit -> KeypadKey(label = digit.toString(), enabled = enabled) { tap(); onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Spacer(Modifier.size(74.dp))
            KeypadKey(label = "0", enabled = enabled) { tap(); onDigit(0) }
            KeypadKey(enabled = enabled, onClick = { tap(); onBackspace() }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KeypadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "keyScale",
    )
    val borderAlpha by animateDpAsState(if (pressed) 1.4.dp else 0.8.dp, label = "keyBorder")
    val fill = if (pressed) {
        Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.22f), scheme.primary.copy(alpha = 0.10f)))
    } else {
        Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainer))
    }
    val borderColor = if (pressed) scheme.primary.copy(alpha = 0.6f) else scheme.onSurface.copy(alpha = 0.1f)
    Box(
        modifier = Modifier
            .size(74.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(borderAlpha, borderColor), CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

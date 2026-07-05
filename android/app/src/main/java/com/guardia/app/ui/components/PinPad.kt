package com.guardia.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

/** PIN dots indicator. */
@Composable
fun PinDots(length: Int, max: Int = 6, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(max) { index ->
            val filled = index < length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
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
            Spacer(Modifier.size(72.dp))
            KeypadKey(label = "0", enabled = enabled) { tap(); onDigit(0) }
            KeypadKey(enabled = enabled, onClick = { tap(); onBackspace() }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    KeypadKey(enabled = enabled, onClick = onClick) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeypadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.4f else 0.15f),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

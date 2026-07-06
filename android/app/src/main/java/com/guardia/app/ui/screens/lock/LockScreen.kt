package com.guardia.app.ui.screens.lock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.components.PinDots
import com.guardia.app.ui.components.PinPad
import com.guardia.app.ui.theme.GuardiaHeroGradient
import com.guardia.app.ui.theme.OverlineStyle
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onDecoy: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // True once the current entry has produced a wrong (sub-max-length) result, so backspacing the
    // buffer to empty still counts as one failed attempt (prevents short-PIN brute cycling).
    var sawWrong by remember { mutableStateOf(false) }

    val lockedUntil by viewModel.lockedUntil.collectAsStateWithLifecycle()
    val attempts by viewModel.failedAttempts.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = lockedUntil > now
    val remainingSec = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    if (locked && pin.isNotEmpty()) pin = ""

    fun registerWrong() {
        error = true
        pin = ""
        sawWrong = false
        viewModel.onWrongAttempt()
    }

    fun attempt(current: String) {
        viewModel.verify(current) { type ->
            when (type) {
                PinType.REAL -> { viewModel.onSuccess(); onUnlocked() }
                PinType.DECOY -> { viewModel.onSuccess(); onDecoy() }
                PinType.PANIC -> { viewModel.onSuccess(); onDecoy() }
                null -> { if (current.length >= 6) registerWrong() else sawWrong = true }
            }
        }
    }

    val attemptsLeft = AppPreferences.LOCK_AFTER_ATTEMPTS - attempts
    val statusLabel = when {
        locked -> "LOCKED OUT"
        error -> "ACCESS DENIED"
        else -> "SECURED"
    }
    val statusColor = if (error || locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val subtitle = when {
        locked -> "Too many attempts. Try again in ${remainingSec}s"
        error && attempts in 1 until AppPreferences.LOCK_AFTER_ATTEMPTS && attemptsLeft <= 2 ->
            "Incorrect PIN - $attemptsLeft attempt${if (attemptsLeft == 1) "" else "s"} left"
        error -> "Incorrect PIN, try again"
        else -> "Enter your PIN to unlock Guardia"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LockMark(alert = error || locked)
        Spacer(Modifier.height(24.dp))
        Text("GUARDIA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.size(8.dp))
            Text(statusLabel, style = OverlineStyle, color = statusColor)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error || locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        PinDots(length = pin.length)
        Spacer(Modifier.height(48.dp))
        PinPad(
            enabled = !locked,
            onDigit = { digit ->
                if (!locked && pin.length < 6) {
                    error = false
                    pin += digit.toString()
                    if (pin.length >= 4) attempt(pin)
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    if (pin.isEmpty() && sawWrong) registerWrong()
                }
            },
        )
    }
}

/** Brand mark for the lock screen: a gradient shield in a slowly rotating scan ring. */
@Composable
private fun LockMark(alert: Boolean) {
    val transition = rememberInfiniteTransition(label = "lockMark")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "sweep",
    )
    val ring = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center) {
        // Ambient glow.
        Box(
            Modifier.size(150.dp).background(
                Brush.radialGradient(listOf(ring.copy(alpha = 0.18f), Color.Transparent)),
                CircleShape,
            ),
        )
        // Rotating scan ring.
        androidx.compose.foundation.Canvas(Modifier.size(112.dp)) {
            val stroke = 2.dp.toPx()
            drawCircle(color = ring.copy(alpha = 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, ring)),
                startAngle = sweep,
                sweepAngle = 110f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        // Center gradient shield.
        val errorColor = MaterialTheme.colorScheme.error
        val shieldBrush = if (alert) {
            Brush.linearGradient(listOf(errorColor, errorColor.copy(alpha = 0.7f)))
        } else {
            GuardiaHeroGradient
        }
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(shieldBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

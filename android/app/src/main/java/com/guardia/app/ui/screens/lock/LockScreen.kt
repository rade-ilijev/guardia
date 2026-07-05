package com.guardia.app.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.components.PinDots
import com.guardia.app.ui.components.PinPad
import com.guardia.app.ui.theme.GuardiaHeroGradient
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
                PinType.PANIC -> {
                    // Dev-safe: never actually wipes. Routes to decoy-like state.
                    viewModel.onSuccess(); onDecoy()
                }
                null -> {
                    if (current.length >= 6) registerWrong() else sawWrong = true
                }
            }
        }
    }

    val attemptsLeft = AppPreferences.LOCK_AFTER_ATTEMPTS - attempts
    val subtitle = when {
        locked -> "Too many attempts. Try again in ${remainingSec}s"
        error && attempts in 1 until AppPreferences.LOCK_AFTER_ATTEMPTS && attemptsLeft <= 2 ->
            "Incorrect PIN - $attemptsLeft attempt${if (attemptsLeft == 1) "" else "s"} left"
        error -> "Incorrect PIN, try again"
        else -> "Enter your PIN to unlock"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(GuardiaHeroGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Guardia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
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
                    // Clearing a known-wrong buffer counts as a completed failed attempt.
                    if (pin.isEmpty() && sawWrong) registerWrong()
                }
            },
        )
    }
}

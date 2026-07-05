package com.guardia.app.core.guard

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.components.PinDots
import com.guardia.app.ui.components.PinPad
import com.guardia.app.ui.theme.GuardiaHeroGradient
import com.guardia.app.ui.theme.GuardiaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PIN gate for turning guarding OFF from outside the app (Quick Settings tile). Without this,
 * anyone holding the unlocked phone could disable protection in one tap from the shade.
 * Mirrors [com.guardia.app.ui.screens.lock.LockScreen]'s attempt accounting so the same
 * failed-attempt lockout applies and partial entries of a longer PIN are never counted.
 */
@AndroidEntryPoint
class StopGuardActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Never leave a user without a PIN stuck with guarding on (shouldn't happen post-onboarding).
        lifecycleScope.launch {
            if (!prefs.pinIsSet.first()) stopAndFinish()
        }

        setContent {
            GuardiaTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    com.guardia.app.ui.components.GuardiaBackdrop()
                    StopGuardContent(
                        prefs = prefs,
                        onVerify = ::verify,
                        onWrongAttempt = { lifecycleScope.launch { prefs.recordPinFailure() } },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun verify(pin: String, onResult: (PinType?) -> Unit) {
        lifecycleScope.launch {
            if (System.currentTimeMillis() < prefs.pinLockedUntil.first()) {
                onResult(null)
                return@launch
            }
            val type = prefs.verifyPin(pin)
            if (type == PinType.REAL) {
                prefs.recordPinSuccess()
                stopAndFinish()
            }
            onResult(type)
        }
    }

    private suspend fun stopAndFinish() {
        prefs.setGuardingEnabled(false)
        GuardController.stop(this)
        finish()
    }
}

@Composable
private fun StopGuardContent(
    prefs: AppPreferences,
    onVerify: (String, (PinType?) -> Unit) -> Unit,
    onWrongAttempt: () -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // True once the current entry produced a wrong sub-max-length result (see LockScreen).
    var sawWrong by remember { mutableStateOf(false) }

    val lockedUntil by prefs.pinLockedUntil.collectAsStateWithLifecycle(0L)
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
        onWrongAttempt()
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
            modifier = Modifier.size(80.dp).clip(CircleShape).background(GuardiaHeroGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.GppBad, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Stop guarding?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                locked -> "Too many attempts. Try again in ${remainingSec}s"
                error -> "Incorrect PIN, try again"
                else -> "Enter your Guardia PIN to turn protection off"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (error || locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        PinDots(length = pin.length)
        Spacer(Modifier.height(44.dp))
        PinPad(
            enabled = !locked,
            onDigit = { digit ->
                if (!locked && pin.length < 6) {
                    error = false
                    pin += digit.toString()
                    if (pin.length >= 4) {
                        onVerify(pin) { type ->
                            if (type == null) {
                                if (pin.length >= 6) registerWrong() else sawWrong = true
                            } else if (type != PinType.REAL) {
                                // Decoy/panic PINs don't stop guarding; treat as wrong.
                                registerWrong()
                            }
                        }
                    }
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    if (pin.isEmpty() && sawWrong) registerWrong()
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("Keep protecting") }
    }
}

package com.guardia.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    private fun <T> flow(f: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val pinIsSet: StateFlow<Boolean> = flow(prefs.pinIsSet, true)
    val decoySet: StateFlow<Boolean> = flow(prefs.decoyPinSet, false)
    val panicSet: StateFlow<Boolean> = flow(prefs.panicPinSet, false)
    /** Epoch-ms until which PIN entry is locked out (0 = open) — same backoff as the lock screen. */
    val lockedUntil: StateFlow<Long> = flow(prefs.pinLockedUntil, 0L)
    /** Grace period, in seconds, before a backgrounded Guardia asks for the PIN again (0 = at once). */
    val relockAfterSeconds: StateFlow<Int> = flow(prefs.relockAfterSeconds, 0)

    fun setRelockAfterSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setRelockAfterSeconds(seconds) }
    }

    /**
     * Progressive verification for the gate step: null = keep typing (not wrong yet), true =
     * verified, false = definitively wrong (counts toward the shared brute-force backoff).
     */
    fun verifyCurrent(pin: String, onResult: (Boolean?) -> Unit) {
        viewModelScope.launch {
            if (System.currentTimeMillis() < prefs.pinLockedUntil.first()) {
                onResult(null)
                return@launch
            }
            val role = prefs.verifyPin(pin)
            when {
                role == PinType.REAL -> {
                    prefs.recordPinSuccess()
                    onResult(true)
                }
                pin.length >= 6 -> {
                    prefs.recordPinFailure()
                    onResult(false)
                }
                else -> onResult(null)
            }
        }
    }

    /** Marks a shorter-than-max entry as a failed attempt (user backspaced after a wrong PIN). */
    fun recordWrongAttempt() {
        viewModelScope.launch { prefs.recordPinFailure() }
    }

    val recoveryCodeSet: StateFlow<Boolean> = flow(prefs.recoveryCodeSet, false)

    /**
     * Generates a fresh recovery code, replacing any previous one, and hands it back for display.
     * Used both to create the first code and to replace one the user has lost — replacing is the
     * only safe answer to "I lost it", since the old one cannot be read back.
     */
    fun regenerateRecoveryCode(onGenerated: (String) -> Unit) {
        viewModelScope.launch {
            val code = com.guardia.app.core.security.PinManager.newRecoveryCode()
            prefs.setRecoveryCode(code)
            onGenerated(code)
        }
    }

    /** Sets or replaces one PIN; the others stay valid. */
    fun setPin(type: PinType, pin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (pin.length !in 4..6) {
                onResult(false, "PIN must be 4-6 digits")
                return@launch
            }
            // One code can only mean one thing: a PIN identical to another role's would make
            // unlocks ambiguous, so refuse it.
            val existingRole = prefs.verifyPin(pin)
            if (existingRole != null && existingRole != type) {
                // label(), not the enum's own name: `PinType.PANIC.name.lowercase()` reads as
                // "panıc" on a Turkish phone, and an identifier was never display text anyway.
                onResult(false, "That PIN is already used by your ${label(existingRole).lowercase(java.util.Locale.getDefault())} PIN")
                return@launch
            }
            val ok = prefs.setSinglePin(type, pin)
            onResult(ok, if (ok) "${label(type)} PIN saved" else "Set your real PIN first")
        }
    }

    /** Removes the decoy or panic PIN. */
    fun removePin(type: PinType, onResult: (String) -> Unit) {
        viewModelScope.launch {
            prefs.clearSecondaryPin(type)
            onResult("${label(type)} PIN removed")
        }
    }

    private fun label(type: PinType) = when (type) {
        PinType.REAL -> "Real"
        PinType.DECOY -> "Decoy"
        PinType.PANIC -> "Panic"
    }
}

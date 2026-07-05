package com.guardia.app.ui.screens.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.security.PinType
import com.guardia.app.core.system.IntruderCaptureService
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {

    /** Epoch-ms until which PIN entry is locked out (0 = open). */
    val lockedUntil: StateFlow<Long> =
        prefs.pinLockedUntil.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val failedAttempts: StateFlow<Int> =
        prefs.pinFailedAttempts.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @Volatile private var lastSelfieAt = 0L

    fun verify(pin: String, onResult: (PinType?) -> Unit) {
        viewModelScope.launch {
            // Guard against verifying while locked out (defense in depth; UI also blocks input).
            if (System.currentTimeMillis() < prefs.pinLockedUntil.first()) {
                onResult(null)
                return@launch
            }
            onResult(prefs.verifyPin(pin))
        }
    }

    /** Call when a completed PIN entry was wrong. Increments the backoff and grabs a selfie. */
    fun onWrongAttempt() {
        viewModelScope.launch {
            prefs.recordPinFailure()
            captureSelfieThrottled()
        }
    }

    fun onSuccess() {
        viewModelScope.launch { prefs.recordPinSuccess() }
    }

    private suspend fun captureSelfieThrottled() {
        if (!prefs.captureIntruders.first()) return
        val now = System.currentTimeMillis()
        if (now - lastSelfieAt < SELFIE_THROTTLE_MS) return
        lastSelfieAt = now
        runCatching { IntruderCaptureService.start(context, "Wrong PIN") }
    }

    private companion object {
        const val SELFIE_THROTTLE_MS = 15_000L
    }
}

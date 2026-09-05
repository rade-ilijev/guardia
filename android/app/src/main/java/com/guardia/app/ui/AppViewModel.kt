package com.guardia.app.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppGate { LOADING, ONBOARDING, LOCKED, UNLOCKED, DECOY }

@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _gate = MutableStateFlow(AppGate.LOADING)
    val gate: StateFlow<AppGate> = _gate.asStateFlow()

    private val relockAfterSeconds: StateFlow<Int> =
        prefs.relockAfterSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Uptime at which the app last left the screen while unlocked, or 0 if it hasn't. */
    private var leftAt = 0L

    init {
        viewModelScope.launch {
            val onboarded = prefs.onboarded.first()
            val pinSet = prefs.pinIsSet.first()
            _gate.value = if (!onboarded || !pinSet) AppGate.ONBOARDING else AppGate.LOCKED
        }
    }

    fun onOnboardingComplete() { _gate.value = AppGate.UNLOCKED }
    fun onUnlocked() { _gate.value = AppGate.UNLOCKED }
    fun onDecoy() { _gate.value = AppGate.DECOY }
    fun lock() { _gate.value = AppGate.LOCKED }

    /**
     * The main activity stopped: the user switched apps, went home, or the screen turned off.
     *
     * Previously the PIN was only ever asked for on process start, so once unlocked the app stayed
     * open until the OS happened to kill it — which on a modern phone can be days. A security app
     * whose front door is open for days is not doing its job, so departure is now what starts the
     * clock. [changingConfigurations] is a rotation, not a departure: the activity is recreated
     * within the same frame, and treating that as leaving would re-lock every time the phone turns.
     */
    fun onAppStopped(changingConfigurations: Boolean) {
        if (changingConfigurations) return
        if (_gate.value == AppGate.UNLOCKED) leftAt = SystemClock.elapsedRealtime()
    }

    /**
     * The main activity started again. Re-lock if the app was away for longer than the user's
     * grace period. The decoy gate is left alone on purpose: someone who was shown the "calculator"
     * and returns to it should keep seeing a calculator, not a PIN screen that gives the game away.
     */
    fun onAppStarted() {
        if (_gate.value != AppGate.UNLOCKED || leftAt == 0L) return
        val away = SystemClock.elapsedRealtime() - leftAt
        leftAt = 0L
        if (shouldRelock(away, relockAfterSeconds.value)) _gate.value = AppGate.LOCKED
    }

    companion object {
        /**
         * Whether an absence of [awayMs] is long enough to re-lock, given a grace period of
         * [graceSeconds].
         *
         * Separated from [onAppStarted] so the rule can be tested without an Android clock. The
         * comparison is `>=`, not `>`, which is what makes the default grace of 0 mean "always
         * re-lock": a same-millisecond return is still a departure.
         */
        fun shouldRelock(awayMs: Long, graceSeconds: Int): Boolean =
            awayMs >= graceSeconds * 1000L
    }
}

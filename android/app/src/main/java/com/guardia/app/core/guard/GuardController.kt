package com.guardia.app.core.guard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide owner of guarding state and the start/stop entry point used by the
 * dashboard, Quick Settings tile, voice safeword, and boot receiver.
 *
 * Kept as a simple singleton (no DI) so system components (services/receivers)
 * can drive it without Hilt entry points.
 */
object GuardController {

    private val _state = MutableStateFlow(GuardState.STOPPED)
    val state: StateFlow<GuardState> = _state.asStateFlow()

    /** True while guarding is relaxed because the device is on a trusted Wi-Fi network. */
    val relaxedOnTrustedWifi = MutableStateFlow(false)

    /**
     * True while guarding is *running*, degraded or not.
     *
     * [GuardState.NEEDS_ATTENTION] counts, and that is load-bearing rather than a nicety: this
     * property is what gates per-app face checks, the Quick Settings tile and the widget. Reading
     * it as "state == PROTECTED" would mean that flagging a missing prerequisite silently switched
     * off the very protection the flag is warning about — the guard would stop instead of
     * complaining. Callers that want the distinction should read [state].
     */
    val isProtected: Boolean
        get() = _state.value == GuardState.PROTECTED || _state.value == GuardState.NEEDS_ATTENTION

    fun start(context: Context) {
        if (isProtected) return
        _state.value = GuardState.PROTECTED
        GuardService.start(context.applicationContext)
        refreshWidgets(context)
    }

    fun stop(context: Context) {
        _state.value = GuardState.STOPPED
        GuardService.stop(context.applicationContext)
        refreshWidgets(context)
    }

    /**
     * Redraws any home-screen widgets. Kept here because this object is the one place guarding
     * state actually changes — hooking it anywhere else would eventually drift out of sync.
     * No-ops when no widget is placed, and never throws into a caller.
     */
    private fun refreshWidgets(context: Context) {
        runCatching {
            com.guardia.app.core.widget.GuardiaWidget.refresh(context.applicationContext)
        }
    }

    fun toggle(context: Context) {
        if (isProtected) stop(context) else start(context)
    }

    /** Called by [GuardService] lifecycle callbacks to keep state in sync. */
    internal fun onServiceState(state: GuardState) {
        _state.value = state
    }

    /**
     * Re-evaluates whether a running guard still has what it needs, and moves it between
     * [GuardState.PROTECTED] and [GuardState.NEEDS_ATTENTION].
     *
     * [dependsOnAccessibility] is passed in rather than read here because this object deliberately
     * has no dependencies — it is driven by system components that cannot reach Hilt.
     *
     * Does nothing while stopped or paused: a prerequisite that is missing for something not
     * running is not a problem, and overwriting PAUSED would lose the user's own choice.
     */
    fun refreshPrerequisites(context: Context, dependsOnAccessibility: Boolean) {
        if (!isProtected) return
        val ok = !dependsOnAccessibility ||
            com.guardia.app.core.system.AccessibilityAccess.refresh(context)
        val next = if (ok) GuardState.PROTECTED else GuardState.NEEDS_ATTENTION
        if (_state.value == next) return
        _state.value = next
        if (ok) com.guardia.app.core.system.ProtectionWarning.clear(context)
        refreshWidgets(context)
    }
}

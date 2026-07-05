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

    val isProtected: Boolean
        get() = _state.value == GuardState.PROTECTED

    fun start(context: Context) {
        if (_state.value == GuardState.PROTECTED) return
        _state.value = GuardState.PROTECTED
        GuardService.start(context.applicationContext)
    }

    fun stop(context: Context) {
        _state.value = GuardState.STOPPED
        GuardService.stop(context.applicationContext)
    }

    fun toggle(context: Context) {
        if (isProtected) stop(context) else start(context)
    }

    /** Called by [GuardService] lifecycle callbacks to keep state in sync. */
    internal fun onServiceState(state: GuardState) {
        _state.value = state
    }
}

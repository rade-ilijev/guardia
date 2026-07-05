package com.guardia.app.core.billing

import com.guardia.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for whether premium features (App Lock, Profiles, Alerts) are unlocked.
 * Debug builds bypass billing so the full feature set is testable without a Play purchase.
 */
@Singleton
class EntitlementManager @Inject constructor() {

    private val _premium = MutableStateFlow(BuildConfig.DEBUG)
    val premium: StateFlow<Boolean> = _premium.asStateFlow()

    val isPremium: Boolean get() = _premium.value

    fun setPremium(value: Boolean) {
        // Never downgrade below the debug bypass.
        _premium.value = value || BuildConfig.DEBUG
    }
}

package com.guardia.app.core.billing

import com.guardia.app.BuildConfig
import com.guardia.app.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for whether premium features (App Lock, Profiles, Alerts) are unlocked.
 *
 * The last-known entitlement is cached in [AppPreferences] and restored on startup, so a paying
 * subscriber keeps premium while offline or before Play Billing has connected. Billing refreshes
 * this once it reports the real purchase state. Debug builds bypass billing so the full feature
 * set is testable without a Play purchase.
 */
@Singleton
class EntitlementManager @Inject constructor(
    private val prefs: AppPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _premium = MutableStateFlow(BuildConfig.DEBUG)
    val premium: StateFlow<Boolean> = _premium.asStateFlow()

    val isPremium: Boolean get() = _premium.value

    init {
        scope.launch {
            if (prefs.premiumCached.first()) _premium.value = true
        }
    }

    fun setPremium(value: Boolean) {
        // Never downgrade below the debug bypass.
        _premium.value = value || BuildConfig.DEBUG
        // Persist the real (non-debug-forced) value so it survives restarts/offline.
        scope.launch { prefs.setPremiumCached(value) }
    }
}

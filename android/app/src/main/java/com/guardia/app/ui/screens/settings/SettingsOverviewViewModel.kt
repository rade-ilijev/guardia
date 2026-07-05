package com.guardia.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.billing.EntitlementManager
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Supplies short "current value" labels shown on the right of each settings row. */
@HiltViewModel
class SettingsOverviewViewModel @Inject constructor(
    prefs: AppPreferences,
    people: PeopleRepository,
    entitlements: EntitlementManager,
) : ViewModel() {

    val premium: StateFlow<Boolean> = entitlements.premium

    private val guardingLabel = combine(
        prefs.responsiveness,
        prefs.intervalCheckEnabled,
        prefs.customIntervalSeconds,
        entitlements.premium,
    ) { responsiveness, intervalEnabled, customInterval, premium ->
        when {
            !intervalEnabled -> "App-open only"
            premium && customInterval > 0 -> formatInterval(customInterval)
            else -> when (responsiveness) {
                0 -> "Saver"
                2 -> "Max"
                else -> "Balanced"
            }
        }
    }

    private val otherLabels = combine(
        prefs.voiceListeningMode,
        prefs.lockedApps,
        prefs.triggerApps,
    ) { voiceMode, lockedApps, triggerApps ->
        buildMap {
            put("appcheck", if (triggerApps.isEmpty()) "Off" else "${triggerApps.size} apps")
            put("applock", if (lockedApps.isEmpty()) "Off" else "${lockedApps.size} apps")
            put("voice", when (voiceMode) {
                1 -> "On"
                2 -> "Fallback"
                else -> "Off"
            })
        }
    }

    private val prefStates = combine(guardingLabel, otherLabels) { guarding, other ->
        other + ("guarding" to guarding)
    }

    private fun formatInterval(seconds: Int): String =
        if (seconds >= 60 && seconds % 60 == 0) "Every ${seconds / 60} min" else "Every ${seconds}s"

    val states: StateFlow<Map<String, String>> = combine(prefStates, people.people) { base, list ->
        val allowed = list.count { !it.blocked }
        val blocked = list.count { it.blocked }
        base + buildMap {
            put("people", if (allowed == 0) "None" else "$allowed")
            put("blocked", if (blocked == 0) "Off" else "$blocked")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}

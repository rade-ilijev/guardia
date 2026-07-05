package com.guardia.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A guarding profile is a named preset that writes the core guarding settings at once.
 * The active profile is remembered so the UI can highlight it.
 */
data class GuardingProfile(
    val name: String,
    val description: String,
    val responsiveness: Int,
    val sensitivity: Float,
    val multiFace: Boolean,
    val capture: Boolean,
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val prefs: AppPreferences,
    entitlements: com.guardia.app.core.billing.EntitlementManager,
) : ViewModel() {

    val premium: StateFlow<Boolean> = entitlements.premium

    val profiles = listOf(
        GuardingProfile("Home", "Relaxed. Fewer checks to save battery at home.", responsiveness = 0, sensitivity = 0.45f, multiFace = false, capture = false),
        GuardingProfile("Work", "Balanced protection with intruder capture.", responsiveness = 1, sensitivity = 0.6f, multiFace = false, capture = true),
        GuardingProfile("Public", "Maximum security for crowded or risky places.", responsiveness = 2, sensitivity = 0.75f, multiFace = true, capture = true),
    )

    val activeProfile: StateFlow<String> = prefs.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default")

    fun apply(profile: GuardingProfile) {
        viewModelScope.launch {
            prefs.setResponsiveness(profile.responsiveness)
            prefs.setSensitivity(profile.sensitivity)
            prefs.setLockOnMultipleFaces(profile.multiFace)
            prefs.setCaptureIntruders(profile.capture)
            prefs.setActiveProfile(profile.name)
        }
    }
}

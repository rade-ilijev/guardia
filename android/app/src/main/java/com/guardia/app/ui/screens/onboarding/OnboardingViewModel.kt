package com.guardia.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.billing.EntitlementManager
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences,
    peopleRepository: PeopleRepository,
    entitlements: EntitlementManager,
) : ViewModel() {

    /** Whether at least one face has been enrolled (drives the enroll-step check mark). */
    val hasEnrolledFace: StateFlow<Boolean> = peopleRepository.people
        .map { list -> list.any { !it.blocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val premium: StateFlow<Boolean> = entitlements.premium

    /** Persists PINs without finishing onboarding (so the user can keep walking the steps). */
    fun savePins(real: String, decoy: String?, panic: String?, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            prefs.setPins(real, decoy, panic)
            onSaved()
        }
    }

    /** Marks onboarding complete and routes into the app. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setOnboarded(true)
            onDone()
        }
    }
}

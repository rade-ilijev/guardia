package com.guardia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppGate { LOADING, ONBOARDING, LOCKED, UNLOCKED, DECOY }

@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _gate = MutableStateFlow(AppGate.LOADING)
    val gate: StateFlow<AppGate> = _gate.asStateFlow()

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
}

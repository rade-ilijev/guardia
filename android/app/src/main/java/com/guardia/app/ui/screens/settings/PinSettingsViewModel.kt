package com.guardia.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    /**
     * Verifies the current real PIN then replaces all PINs. Reports the outcome via [onResult].
     */
    fun changePins(
        currentPin: String,
        newReal: String,
        newDecoy: String?,
        newPanic: String?,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch {
            val role = prefs.verifyPin(currentPin)
            if (role != com.guardia.app.core.security.PinType.REAL) {
                onResult(false, "Current PIN is incorrect")
                return@launch
            }
            if (newReal.length !in 4..6) {
                onResult(false, "New PIN must be 4-6 digits")
                return@launch
            }
            if ((newDecoy != null && newDecoy == newReal) || (newPanic != null && newPanic == newReal)) {
                onResult(false, "Decoy/Panic PINs must differ from the real PIN")
                return@launch
            }
            prefs.setPins(newReal, newDecoy, newPanic)
            onResult(true, "PINs updated")
        }
    }
}

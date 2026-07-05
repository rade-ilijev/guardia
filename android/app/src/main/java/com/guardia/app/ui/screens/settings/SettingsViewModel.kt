package com.guardia.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.voice.VoiceController
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.data.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val events: EventsRepository,
    private val intruders: IntruderRepository,
    private val people: PeopleRepository,
    entitlements: com.guardia.app.core.billing.EntitlementManager,
) : ViewModel() {

    val premium: StateFlow<Boolean> = entitlements.premium
    val responsiveness: StateFlow<Int> = prefs.responsiveness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val intervalCheckEnabled: StateFlow<Boolean> = prefs.intervalCheckEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val customIntervalSeconds: StateFlow<Int> = prefs.customIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val firstCheckOnUnlock: StateFlow<Boolean> = prefs.firstCheckOnUnlock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shakeToCheck: StateFlow<Boolean> = prefs.shakeToCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val checkRamp: StateFlow<List<Int>> = prefs.checkRamp
        .map { parseRamp(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sensitivity: StateFlow<Float> = prefs.sensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.65f)
    val lockOnUnknownFace: StateFlow<Boolean> = prefs.lockOnUnknownFace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lockOnBlockedPerson: StateFlow<Boolean> = prefs.lockOnBlockedPerson
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lockOnMultipleFaces: StateFlow<Boolean> = prefs.lockOnMultipleFaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lockOnNoFace: StateFlow<Boolean> = prefs.lockOnNoFace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val captureIntruders: StateFlow<Boolean> = prefs.captureIntruders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    /** Consecutive failed device unlocks before a wrong-unlock selfie is captured (1 = every one). */
    val wrongUnlockThreshold: StateFlow<Int> = prefs.wrongUnlockThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val lowLightAction: StateFlow<Int> = prefs.lowLightAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val voiceListeningMode: StateFlow<Int> = prefs.voiceListeningMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val testMode: StateFlow<Boolean> = prefs.testMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** Opt-in local crash log (never uploaded). */
    val crashLogEnabled: StateFlow<Boolean> = prefs.crashLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setCrashLogEnabled(value: Boolean) = viewModelScope.launch { prefs.setCrashLogEnabled(value) }
    fun readCrashLog(): String = com.guardia.app.core.system.CrashLogger.read(context)
    fun clearCrashLog() = com.guardia.app.core.system.CrashLogger.clear(context)

    fun setResponsiveness(level: Int) = viewModelScope.launch { prefs.setResponsiveness(level) }
    fun setIntervalCheckEnabled(value: Boolean) = viewModelScope.launch { prefs.setIntervalCheckEnabled(value) }
    fun setCustomIntervalSeconds(seconds: Int) = viewModelScope.launch { prefs.setCustomIntervalSeconds(seconds) }
    fun setFirstCheckOnUnlock(value: Boolean) = viewModelScope.launch { prefs.setFirstCheckOnUnlock(value) }
    fun setShakeToCheck(value: Boolean) = viewModelScope.launch { prefs.setShakeToCheck(value) }

    fun setRampList(steps: List<Int>) = updateRamp(steps)

    private fun updateRamp(steps: List<Int>) = viewModelScope.launch {
        prefs.setCheckRamp(steps.joinToString(",") { it.toString() })
    }

    private fun parseRamp(value: String): List<Int> =
        value.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    fun setSensitivity(value: Float) = viewModelScope.launch { prefs.setSensitivity(value) }
    fun setLockOnUnknownFace(value: Boolean) = viewModelScope.launch { prefs.setLockOnUnknownFace(value) }
    fun setLockOnBlockedPerson(value: Boolean) = viewModelScope.launch { prefs.setLockOnBlockedPerson(value) }
    fun setLockOnMultipleFaces(value: Boolean) = viewModelScope.launch { prefs.setLockOnMultipleFaces(value) }
    fun setLockOnNoFace(value: Boolean) = viewModelScope.launch { prefs.setLockOnNoFace(value) }
    fun setCaptureIntruders(value: Boolean) = viewModelScope.launch { prefs.setCaptureIntruders(value) }
    fun setWrongUnlockThreshold(value: Int) = viewModelScope.launch { prefs.setWrongUnlockThreshold(value.coerceIn(1, 5)) }
    fun setLowLightAction(value: Int) = viewModelScope.launch { prefs.setLowLightAction(value) }

    fun setVoiceMode(mode: Int) = viewModelScope.launch {
        prefs.setVoiceListeningMode(mode)
        when (mode) {
            1 -> VoiceController.start(context)
            0 -> VoiceController.stop(context)
        }
    }

    fun setTestMode(value: Boolean) = viewModelScope.launch { prefs.setTestMode(value) }

    fun clearActivityLog() = viewModelScope.launch { events.clear() }
    fun clearIntruderPhotos() = viewModelScope.launch { intruders.clear() }

    /** Deletes cached face crops that aren't referenced by any sample/negative/avatar. */
    fun cleanupUnusedPhotos(onResult: (Int) -> Unit) = viewModelScope.launch {
        val referenced = people.referencedPhotoPaths()
        val removed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.filesDir, "faces")
            val files = dir.listFiles()?.filter { it.isFile } ?: return@withContext 0
            var count = 0
            for (file in files) {
                if (file.absolutePath !in referenced && file.delete()) count++
            }
            count
        }
        onResult(removed)
    }

    private companion object {
        const val DEFAULT_RAMP_STEP = 10
    }
}

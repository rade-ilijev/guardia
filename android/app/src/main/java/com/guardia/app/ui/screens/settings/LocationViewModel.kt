package com.guardia.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.billing.EntitlementManager
import com.guardia.app.core.location.LocationZoneManager
import com.guardia.app.core.location.ZonePolicy
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.SafeZoneRepository
import com.guardia.app.domain.model.SafeZone
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val zoneRepo: SafeZoneRepository,
    private val zoneManager: LocationZoneManager,
    entitlements: EntitlementManager,
) : ViewModel() {

    val premium: StateFlow<Boolean> = entitlements.premium
    val locationMode: StateFlow<Boolean> = prefs.locationModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val publicGuard: StateFlow<Boolean> = prefs.publicGuardEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val publicUseDefault: StateFlow<Boolean> = prefs.publicUseDefault
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val publicResponsiveness: StateFlow<Int> = prefs.publicResponsiveness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val publicCustomInterval: StateFlow<Int> = prefs.publicCustomIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val publicFirstCheck: StateFlow<Boolean> = prefs.publicFirstCheckOnUnlock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val publicRamp: StateFlow<List<Int>> = prefs.publicCheckRamp
        .map { parseRamp(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val publicShake: StateFlow<Boolean> = prefs.publicShakeToCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val publicLockOnNoFace: StateFlow<Boolean> = prefs.publicLockOnNoFace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val zones: StateFlow<List<SafeZone>> = zoneRepo.zones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val policy: StateFlow<ZonePolicy> = zoneManager.policy

    /** Transient message for the UI (e.g. couldn't get a location fix). */
    val message = MutableStateFlow<String?>(null)
    fun consumeMessage() { message.value = null }

    init {
        // Keep a live location fix while the screen is open so the status reflects reality, and
        // re-poll periodically so leaving/entering a zone updates without any manual retrigger.
        if (hasForegroundLocation()) {
            zoneManager.start()
            viewModelScope.launch {
                while (true) {
                    zoneManager.refreshNow()
                    kotlinx.coroutines.delay(REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    fun hasForegroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun onPermissionGranted() {
        zoneManager.start()
        zoneManager.refreshNow()
    }

    /** Re-check the current location on demand (e.g. screen resumed or pull-to-refresh). */
    fun refreshLocation() = zoneManager.refreshNow()

    fun setLocationMode(value: Boolean) = viewModelScope.launch { prefs.setLocationModeEnabled(value) }
    fun setPublicGuard(value: Boolean) = viewModelScope.launch { prefs.setPublicGuardEnabled(value) }
    fun setPublicUseDefault(value: Boolean) = viewModelScope.launch { prefs.setPublicUseDefault(value) }
    fun setPublicResponsiveness(level: Int) = viewModelScope.launch { prefs.setPublicResponsiveness(level) }
    fun setPublicCustomInterval(seconds: Int) = viewModelScope.launch { prefs.setPublicCustomIntervalSeconds(seconds) }
    fun setPublicFirstCheck(value: Boolean) = viewModelScope.launch { prefs.setPublicFirstCheckOnUnlock(value) }
    fun setPublicRamp(steps: List<Int>) = viewModelScope.launch {
        prefs.setPublicCheckRamp(steps.joinToString(",") { it.toString() })
    }
    fun setPublicShake(value: Boolean) = viewModelScope.launch { prefs.setPublicShakeToCheck(value) }
    fun setPublicLockOnNoFace(value: Boolean) = viewModelScope.launch { prefs.setPublicLockOnNoFace(value) }

    fun addCurrentLocation(name: String) = viewModelScope.launch {
        val loc = zoneManager.currentLocation()
        if (loc == null) {
            message.value = "Couldn't get your location. Make sure location is on and permission is granted."
            return@launch
        }
        zoneRepo.add(name = name, latitude = loc.latitude, longitude = loc.longitude)
        message.value = "Saved \"$name\" at your current location."
    }

    fun rename(id: String, name: String) = viewModelScope.launch { zoneRepo.rename(id, name) }
    fun setRadius(id: String, radius: Int) = viewModelScope.launch { zoneRepo.setRadius(id, radius) }
    fun setZoneGuard(id: String, enabled: Boolean) = viewModelScope.launch { zoneRepo.setGuardEnabled(id, enabled) }
    fun setZoneUseDefault(id: String, value: Boolean) = viewModelScope.launch { zoneRepo.setUseDefault(id, value) }
    fun setZoneResponsiveness(id: String, level: Int) = viewModelScope.launch { zoneRepo.setResponsiveness(id, level) }
    fun setZoneCustomInterval(id: String, seconds: Int) = viewModelScope.launch { zoneRepo.setCustomIntervalSeconds(id, seconds) }
    fun setZoneFirstCheck(id: String, value: Boolean) = viewModelScope.launch { zoneRepo.setFirstCheckOnUnlock(id, value) }
    fun setZoneRamp(id: String, steps: List<Int>) = viewModelScope.launch {
        zoneRepo.setCheckRamp(id, steps.joinToString(",") { it.toString() })
    }
    fun setZoneShake(id: String, value: Boolean) = viewModelScope.launch { zoneRepo.setShakeToCheck(id, value) }
    fun setZoneLockOnNoFace(id: String, enabled: Boolean) = viewModelScope.launch { zoneRepo.setLockOnNoFace(id, enabled) }
    fun deleteZone(id: String) = viewModelScope.launch { zoneRepo.delete(id) }

    private fun parseRamp(value: String): List<Int> =
        value.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }

    private companion object {
        const val REFRESH_INTERVAL_MS = 20_000L
    }
}

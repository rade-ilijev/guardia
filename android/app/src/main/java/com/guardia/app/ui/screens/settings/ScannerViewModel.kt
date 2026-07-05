package com.guardia.app.ui.screens.settings

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FixAction { NONE, DEVICE_ADMIN, SECURITY_SETTINGS, ACCESSIBILITY, APP_DETAILS, DEVELOPER, IN_APP }

data class SecurityCheck(
    val title: String,
    val passed: Boolean,
    val detail: String,
    val fix: FixAction = FixAction.NONE,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _checks = MutableStateFlow<List<SecurityCheck>>(emptyList())
    val checks: StateFlow<List<SecurityCheck>> = _checks.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            val pinSet = runCatching { prefs.pinIsSet.first() }.getOrDefault(false)
            val guarding = runCatching { prefs.guardingEnabled.first() }.getOrDefault(false)
            val results = buildList {
                addCheck("Screen lock enabled", "A PIN, pattern, or biometric lock protects the lock screen.", FixAction.SECURITY_SETTINGS) {
                    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
                }
                addCheck("Device admin active", "Lets Guardia lock the device when an intruder is detected.", FixAction.DEVICE_ADMIN) {
                    DeviceAdminManager.isAdminActive(context)
                }
                addCheck("Camera permission", "Required for face recognition.", FixAction.APP_DETAILS) {
                    hasPermission(android.Manifest.permission.CAMERA)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    addCheck("Notifications allowed", "Needed to show guarding status and alerts.", FixAction.APP_DETAILS) {
                        hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                addCheck("App Lock service enabled", "Accessibility service powers per-app locking.", FixAction.ACCESSIBILITY) {
                    isAccessibilityEnabled()
                }
                addCheck("Guardia PIN set", "Your real/decoy/panic PINs are configured.", FixAction.IN_APP) { pinSet }
                addCheck("Guarding active", "Continuous face guarding is currently running.", FixAction.IN_APP) { guarding }
                addCheck("USB debugging off", "USB debugging can expose data to a connected computer.", FixAction.DEVELOPER) {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 0
                }
                addCheck("Unknown app installs restricted", "Installing apps from outside the store increases malware risk.", FixAction.APP_DETAILS) {
                    !canInstallUnknownApps()
                }
            }
            _checks.value = results
            val passed = results.count { it.passed }
            _score.value = if (results.isEmpty()) 0 else (passed * 100) / results.size
        }
    }

    /** Adds a check, treating any failure to evaluate as "not passed" so the screen never crashes. */
    private inline fun MutableList<SecurityCheck>.addCheck(
        title: String,
        detail: String,
        fix: FixAction,
        test: () -> Boolean,
    ) {
        val passed = runCatching { test() }.getOrDefault(false)
        add(SecurityCheck(title, passed, detail, fix))
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun canInstallUnknownApps(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else false

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return flat.contains("${context.packageName}/")
    }
}

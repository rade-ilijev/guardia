package com.guardia.app.ui.screens.settings

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.security.IntegrityGuard
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

enum class FixAction { NONE, DEVICE_ADMIN, SECURITY_SETTINGS, ACCESSIBILITY, APP_DETAILS, DEVELOPER, IN_APP, WIFI }

/** How much a failed check drags down the device's security posture. */
enum class Severity { CRITICAL, RECOMMENDED, INFO }

data class SecurityCheck(
    val title: String,
    val passed: Boolean,
    val detail: String,
    val severity: Severity = Severity.RECOMMENDED,
    val fix: FixAction = FixAction.NONE,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val integrity: IntegrityGuard,
) : ViewModel() {

    private val _checks = MutableStateFlow<List<SecurityCheck>>(emptyList())
    val checks: StateFlow<List<SecurityCheck>> = _checks.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            val pinSet = runCatching { prefs.pinIsSet.first() }.getOrDefault(false)
            val guarding = runCatching { prefs.guardingEnabled.first() }.getOrDefault(false)
            val report = runCatching { integrity.check() }.getOrNull()

            val results = buildList {
                // --- Critical: the things that, if wrong, undermine everything else. ---
                addCheck("Screen lock enabled", "A PIN, pattern, or biometric lock protects the lock screen.", Severity.CRITICAL, FixAction.SECURITY_SETTINGS) {
                    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
                }
                addCheck("Device is not rooted", "Root access lets malware bypass Android's protections entirely.", Severity.CRITICAL, FixAction.NONE) {
                    report?.rooted == false
                }
                addCheck("Guardia is not tampered with", "The app is signed by its original certificate (not repackaged).", Severity.CRITICAL, FixAction.NONE) {
                    report?.signatureValid != false
                }
                addCheck("Device admin active", "Lets Guardia lock the device when an intruder is detected.", Severity.CRITICAL, FixAction.DEVICE_ADMIN) {
                    DeviceAdminManager.isAdminActive(context)
                }
                addCheck("Camera permission", "Required for face recognition.", Severity.CRITICAL, FixAction.APP_DETAILS) {
                    hasPermission(android.Manifest.permission.CAMERA)
                }

                // --- Recommended: strongly advised device hygiene. ---
                addCheck("Security patch is recent", patchDetail(), Severity.RECOMMENDED, FixAction.SECURITY_SETTINGS) {
                    patchAgeDays()?.let { it <= MAX_PATCH_AGE_DAYS } ?: true
                }
                addCheck("Lock screen hides private content", "Notifications on the lock screen don't reveal message contents.", Severity.RECOMMENDED, FixAction.SECURITY_SETTINGS) {
                    Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications", 1) == 0
                }
                addCheck("USB debugging off", "USB debugging can expose data to a connected computer.", Severity.RECOMMENDED, FixAction.DEVELOPER) {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 0
                }
                addCheck("Unknown app installs restricted", "Installing apps from outside the store increases malware risk.", Severity.RECOMMENDED, FixAction.APP_DETAILS) {
                    !canInstallUnknownApps()
                }
                addCheck("On a secured Wi-Fi network", "Open Wi-Fi networks let others on the network snoop on traffic.", Severity.RECOMMENDED, FixAction.WIFI) {
                    !onOpenWifi()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    addCheck("Notifications allowed", "Needed to show guarding status and alerts.", Severity.RECOMMENDED, FixAction.APP_DETAILS) {
                        hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // --- Guardia setup state (info-weight). ---
                addCheck("App Lock service enabled", "Accessibility service powers per-app locking.", Severity.INFO, FixAction.ACCESSIBILITY) {
                    isAccessibilityEnabled()
                }
                addCheck("Guardia PIN set", "Your real/decoy/panic PINs are configured.", Severity.INFO, FixAction.IN_APP) { pinSet }
                addCheck("Guarding active", "Continuous face guarding is currently running.", Severity.INFO, FixAction.IN_APP) { guarding }
            }
            _checks.value = results
            _score.value = weightedScore(results)
        }
    }

    /** Weighted so a failed critical hurts far more than a failed info item. */
    private fun weightedScore(results: List<SecurityCheck>): Int {
        fun weight(s: Severity) = when (s) {
            Severity.CRITICAL -> 5
            Severity.RECOMMENDED -> 2
            Severity.INFO -> 1
        }
        val total = results.sumOf { weight(it.severity) }
        if (total == 0) return 0
        val earned = results.filter { it.passed }.sumOf { weight(it.severity) }
        return (earned * 100) / total
    }

    private inline fun MutableList<SecurityCheck>.addCheck(
        title: String,
        detail: String,
        severity: Severity,
        fix: FixAction,
        test: () -> Boolean,
    ) {
        val passed = runCatching { test() }.getOrDefault(false)
        add(SecurityCheck(title, passed, detail, severity, fix))
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

    /** Days since the OS security patch, or null if unparseable. */
    private fun patchAgeDays(): Long? = runCatching {
        val patch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() } ?: return null
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(patch) ?: return null
        (System.currentTimeMillis() - date.time) / (24 * 60 * 60 * 1000L)
    }.getOrNull()

    private fun patchDetail(): String {
        val age = patchAgeDays() ?: return "Your device's Android security patch level."
        return "Security patch is about $age days old (aim for under $MAX_PATCH_AGE_DAYS)."
    }

    /**
     * Whether the phone is on an open (unencrypted) Wi-Fi network.
     *
     * Guarded twice over. Reading the associated network needs the location grant on modern
     * Android, so without it the answer would not be meaningful anyway and the check reports
     * "not on an open network" rather than guessing; anything the platform still refuses is caught
     * by runCatching. Lint cannot follow either guard through the lambda, hence the annotation.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun onOpenWifi(): Boolean = runCatching {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@runCatching false
        }
        // Reading the current network's security needs an active Wi-Fi connection; treat "unknown"
        // as safe so we never cry wolf. Open networks have no capabilities/empty security.
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? android.net.wifi.WifiManager ?: return false
        if (!wifi.isWifiEnabled) return false
        val info = wifi.connectionInfo ?: return false
        // networkId -1 means not associated with a saved network.
        if (info.networkId == -1) return false
        val ssid = info.ssid ?: return false
        val configured = wifi.configuredNetworks?.firstOrNull { it.networkId == info.networkId }
        // If we can read the saved config, an empty allowedKeyManagement/NONE-only means open.
        configured?.let { cfg ->
            return cfg.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.NONE) &&
                cfg.allowedKeyManagement.cardinality() == 1
        }
        false
    }.getOrDefault(false)

    private companion object {
        const val MAX_PATCH_AGE_DAYS = 120
    }
}

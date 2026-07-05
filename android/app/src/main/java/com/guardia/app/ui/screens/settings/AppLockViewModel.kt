package com.guardia.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    entitlements: com.guardia.app.core.billing.EntitlementManager,
) : ViewModel() {

    val premium: StateFlow<Boolean> = entitlements.premium
    val lockedApps: StateFlow<Set<String>> = prefs.lockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .asSequence()
                    .map { it.activityInfo.packageName }
                    .filter { it != context.packageName }
                    .distinct()
                    .map { pkg ->
                        val label = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault(pkg)
                        InstalledApp(pkg, label)
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
            apps.value = list
        }
    }

    fun toggle(pkg: String) {
        viewModelScope.launch {
            val current = lockedApps.value.toMutableSet()
            if (!current.add(pkg)) current.remove(pkg)
            prefs.setLockedApps(current)
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return flat.contains("${context.packageName}/.core.system.GuardAccessibilityService") ||
            flat.contains("${context.packageName}/com.guardia.app.core.system.GuardAccessibilityService")
    }
}

package com.guardia.app.ui.screens.settings

import android.content.Context
import android.content.Intent
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

@HiltViewModel
class AppCheckViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {

    val triggerApps: StateFlow<Set<String>> = prefs.triggerApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val checkStyle: StateFlow<Int> = prefs.appCheckStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lockOnFail: StateFlow<Boolean> = prefs.appLockOnFail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCheckStyle(style: Int) {
        viewModelScope.launch { prefs.setAppCheckStyle(style) }
    }

    fun setLockOnFail(value: Boolean) {
        viewModelScope.launch { prefs.setAppLockOnFail(value) }
    }

    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    init {
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
            val current = triggerApps.value.toMutableSet()
            if (!current.add(pkg)) current.remove(pkg)
            prefs.setTriggerApps(current)
        }
    }
}

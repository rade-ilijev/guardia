package com.guardia.app.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.security.IntegrityGuard
import com.guardia.app.core.security.SecurityAuditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Live one-line statuses for the Security Center hub cards. All computed on-device. */
@HiltViewModel
class SecurityCenterViewModel @Inject constructor(
    private val auditor: SecurityAuditor,
    private val integrity: IntegrityGuard,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val highRiskApps: Int = 0,
        val integrityOk: Boolean = true,
        val integrityIssue: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var lastRefreshAt = 0L

    /**
     * Re-audits every installed app.
     *
     * This is the expensive one: it walks the full package list and reads each app's granted
     * permissions, so on a phone with a few hundred apps it is hundreds of PackageManager calls.
     * It was running on every resume of the *home* screen, including every time the card scrolled
     * back into view, which is how a summary tile ends up costing more than the screen it
     * summarises. Installed apps and their permissions change on the order of days.
     */
    fun refresh(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && lastRefreshAt != 0L && now - lastRefreshAt < MIN_REAUDIT_MS) return
        lastRefreshAt = now
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val (highRisk, report) = withContext(Dispatchers.Default) {
                val audit = runCatching { auditor.audit() }.getOrDefault(emptyList())
                val high = audit.count { it.risk == SecurityAuditor.Risk.HIGH }
                high to runCatching { integrity.check() }.getOrNull()
            }
            val issue = when {
                report == null -> null
                report.rooted -> "Device appears rooted"
                !report.signatureValid -> "App may be repackaged"
                report.debuggerAttached -> "Debugger attached"
                else -> null
            }
            _ui.value = UiState(
                loading = false,
                highRiskApps = highRisk,
                integrityOk = report?.clean != false,
                integrityIssue = issue,
            )
        }
    }

    private companion object {
        /** Installed apps and their permissions change on the order of days, not seconds. */
        const val MIN_REAUDIT_MS = 120_000L
    }
}

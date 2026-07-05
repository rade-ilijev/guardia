package com.guardia.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.alerts.AlertsManager
import com.guardia.app.core.system.SensitiveComponents
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val alerts: AlertsManager,
    entitlements: com.guardia.app.core.billing.EntitlementManager,
) : ViewModel() {

    private fun <T> flow(f: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val premium: StateFlow<Boolean> = entitlements.premium

    val emailEnabled = flow(prefs.emailAlertsEnabled, false)
    val smtpHost = flow(prefs.smtpHost, "smtp.gmail.com")
    val smtpPort = flow(prefs.smtpPort, 587)
    val smtpUser = flow(prefs.smtpUser, "")
    val smtpPassword = flow(prefs.smtpPassword, "")
    val recipient = flow(prefs.alertRecipient, "")
    val smsEnabled = flow(prefs.smsAlertsEnabled, false)
    val trustedNumber = flow(prefs.trustedNumber, "")
    val findEnabled = flow(prefs.findMyPhoneEnabled, false)
    val findKeyword = flow(prefs.findKeyword, "GUARDIA LOCATE")
    val findTrustedOnly = flow(prefs.findTrustedOnly, true)

    fun setEmailEnabled(v: Boolean) = viewModelScope.launch { prefs.setEmailAlertsEnabled(v) }
    fun setSmtpHost(v: String) = viewModelScope.launch { prefs.setSmtpHost(v) }
    fun setSmtpPort(v: Int) = viewModelScope.launch { prefs.setSmtpPort(v) }
    fun setSmtpUser(v: String) = viewModelScope.launch { prefs.setSmtpUser(v) }
    fun setSmtpPassword(v: String) = viewModelScope.launch { prefs.setSmtpPassword(v) }
    fun setRecipient(v: String) = viewModelScope.launch { prefs.setAlertRecipient(v) }
    fun setSmsEnabled(v: Boolean) = viewModelScope.launch { prefs.setSmsAlertsEnabled(v) }
    fun setTrustedNumber(v: String) = viewModelScope.launch { prefs.setTrustedNumber(v) }
    fun setFindEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setFindMyPhoneEnabled(v)
        SensitiveComponents.setSmsReceiverEnabled(context, v)
    }
    fun setFindKeyword(v: String) = viewModelScope.launch { prefs.setFindKeyword(v) }
    fun setFindTrustedOnly(v: Boolean) = viewModelScope.launch { prefs.setFindTrustedOnly(v) }

    fun sendTest(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val emailOn = prefs.emailAlertsEnabled.first()
            val smsOn = prefs.smsAlertsEnabled.first()
            if (!emailOn && !smsOn) {
                onResult(false, "Enable email or SMS alerts first")
                return@launch
            }
            runCatching { alerts.onSecurityEvent("Test alert from Guardia.", null) }
                .onSuccess { onResult(true, "Test alert sent") }
                .onFailure { onResult(false, "Failed: ${it.message}") }
        }
    }
}

package com.guardia.app.core.alerts

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Acts on a secret keyword SMS from any number: replies with the device's last-known location
 * and locks the device. Disabled unless the user enabled find-my-phone and set a keyword.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var alerts: AlertsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        val sender = messages.firstOrNull()?.originatingAddress ?: return

        val pending = goAsync()
        scope.launch {
            try {
                if (!prefs.findMyPhoneEnabled.first()) return@launch
                val keyword = prefs.findKeyword.first().trim()
                if (keyword.isEmpty() || !body.equals(keyword, ignoreCase = true)) return@launch
                // With "trusted number only" on (the default), a leaked keyword alone can't be used
                // by a stranger to lock/locate the device: the text must come from the trusted number.
                if (prefs.findTrustedOnly.first()) {
                    val trusted = prefs.trustedNumber.first().trim()
                    if (trusted.isEmpty() || !numbersMatch(sender, trusted)) return@launch
                }
                handleLocate(context, sender)
            } finally {
                pending.finish()
            }
        }
    }

    /** Loose phone-number equality that survives formatting/country-prefix differences. */
    private fun numbersMatch(a: String, b: String): Boolean {
        val da = a.filter(Char::isDigit)
        val db = b.filter(Char::isDigit)
        if (da.isEmpty() || db.isEmpty()) return false
        val tail = minOf(da.length, db.length, 9)
        return da.takeLast(tail) == db.takeLast(tail)
    }

    private suspend fun handleLocate(context: Context, sender: String) {
        // Lock the device first.
        runCatching { DeviceAdminManager.lockNow(context) }

        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val reply = if (hasLocation) {
            val loc = lastLocation(context)
            if (loc != null) {
                "Guardia: device locked. Location: https://maps.google.com/?q=${loc.first},${loc.second}"
            } else {
                "Guardia: device locked. Location unavailable right now."
            }
        } else {
            "Guardia: device locked. Location permission not granted."
        }
        runCatching { alerts.sendSms(sender, reply) }
    }

    private suspend fun lastLocation(context: Context): Pair<Double, Double>? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val task = client.lastLocation
            val loc = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                task.addOnSuccessListener { cont.resume(it) {} }
                    .addOnFailureListener { cont.resume(null) {} }
            }
            loc?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            null
        }
    }
}

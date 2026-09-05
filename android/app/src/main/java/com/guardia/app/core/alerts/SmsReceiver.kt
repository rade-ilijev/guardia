package com.guardia.app.core.alerts

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.guardia.app.core.guard.GuardController
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
 * Acts on secret keyword texts (find-my-phone): a LOCATE keyword replies with the device's
 * location and locks it; an ARM keyword turns guarding on. Disabled unless the user enabled
 * find-my-phone. By default both keywords are honored only from the trusted number.
 *
 * SMS is a full-build-only capability — the Play build ships without SMS permissions and never
 * registers this receiver.
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
                val locateKeyword = prefs.findKeyword.first().trim()
                val armKeyword = prefs.armKeyword.first().trim()

                val command = when {
                    locateKeyword.isNotEmpty() && body.equals(locateKeyword, ignoreCase = true) -> Command.LOCATE
                    armKeyword.isNotEmpty() && body.equals(armKeyword, ignoreCase = true) -> Command.ARM
                    else -> return@launch
                }

                // With "trusted number only" on (the default), a leaked keyword alone can't be used
                // by a stranger: the text must come from the trusted number. In open mode the two
                // built-in defaults are never honored (they ship in the app, so anyone knows them).
                if (prefs.findTrustedOnly.first()) {
                    val trusted = prefs.trustedNumber.first().trim()
                    if (trusted.isEmpty() || !numbersMatch(sender, trusted)) return@launch
                } else {
                    val matched = if (command == Command.LOCATE) locateKeyword else armKeyword
                    val default = if (command == Command.LOCATE)
                        AppPreferences.DEFAULT_FIND_KEYWORD else AppPreferences.DEFAULT_ARM_KEYWORD
                    if (matched.equals(default, ignoreCase = true)) return@launch
                }

                when (command) {
                    Command.LOCATE -> handleLocate(context, sender)
                    Command.ARM -> handleArm(context, sender)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private enum class Command { LOCATE, ARM }

    private suspend fun handleArm(context: Context, sender: String) {
        runCatching { GuardController.start(context) }
        runCatching { prefs.setGuardingEnabled(true) }
        runCatching { alerts.sendSms(sender, "Guardia: protection turned on.") }
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
            val loc = bestLocation(context)
            if (loc != null) {
                "Guardia: device locked. Location: https://maps.google.com/?q=${loc.first},${loc.second}"
            } else {
                "Guardia: device locked. Location unavailable (turn Location on to enable this)."
            }
        } else {
            "Guardia: device locked. Location permission not granted."
        }
        runCatching { alerts.sendSms(sender, reply) }
    }

    /**
     * Best available position: the cached last-known fix if present, otherwise a fresh
     * high-accuracy fix (this is the honest version of "turn on location if needed" — it actively
     * powers the GPS/network providers up for one reading; it cannot bypass a revoked permission
     * or a device Location toggle the user turned off, which Android forbids any app from doing).
     */
    private suspend fun bestLocation(context: Context): Pair<Double, Double>? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val last = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it) {} }
                    .addOnFailureListener { cont.resume(null) {} }
            }
            if (last != null) return last.latitude to last.longitude

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(20_000)
                .build()
            val fresh = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { cont.resume(it) {} }
                    .addOnFailureListener { cont.resume(null) {} }
            }
            fresh?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            null
        }
    }
}

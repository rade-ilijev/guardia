package com.guardia.app.core.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.guardia.app.R
import com.guardia.app.core.alerts.AlertsManager
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reacts to attempts to disable Guardia's protection (removing Device Admin, turning off the
 * accessibility service). When guarding is enabled it logs the event, grabs an intruder selfie,
 * fires the configured alerts, and warns the owner with a high-priority notification.
 */
@Singleton
class TamperResponder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val events: EventsRepository,
    private val alerts: AlertsManager,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onTamper(reason: String) {
        scope.launch {
            // Only treat it as tampering if the user intended to be protected.
            val guarding = runCatching { prefs.guardingEnabled.first() }.getOrDefault(false)
            if (!guarding) return@launch

            runCatching { events.log(GuardEvent.Type.INFO, "Tamper detected: $reason") }
            runCatching { IntruderCaptureService.start(context, "Tamper: $reason") }
            runCatching { alerts.onSecurityEvent("Guardia tamper detected: $reason", null) }
            notifyOwner(reason)
        }
    }

    private fun notifyOwner(reason: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tamper alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Warns you when someone tries to disable Guardia's protection."
                },
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Guardia protection changed")
            .setContentText(reason)
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "guardia_tamper"
        const val NOTIFICATION_ID = 1003
    }
}

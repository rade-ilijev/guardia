package com.guardia.app.core.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.guardia.app.R
import com.guardia.app.core.guard.GuardController
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the worst silent failure a security app can have: the OS (usually an OEM battery
 * manager) killed the guard foreground service, ending protection without anyone noticing.
 *
 * The running [com.guardia.app.core.guard.GuardService] writes a heartbeat once a minute and marks
 * a clean stop in onDestroy. On process start, if the user wants guarding on but the heartbeat is
 * stale and the last stop wasn't clean, we restart guarding (best-effort), log the gap in the
 * Activity timeline, and tell the owner how to stop it happening again (battery optimization).
 */
@Singleton
class GuardWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val events: EventsRepository,
) {

    suspend fun checkOnProcessStart() {
        if (!prefs.guardingEnabled.first()) return
        val heartbeat = prefs.guardHeartbeatAt.first()
        if (heartbeat == 0L) return
        if (prefs.guardStoppedCleanly.first()) return
        // Shortly after boot the BootReceiver re-arms guarding anyway; a stale heartbeat then is
        // just the reboot, not a battery kill.
        if (SystemClock.elapsedRealtime() < BOOT_GRACE_MS) return
        // Freshness bound: a heartbeat under two maintenance ticks old means the service is (or
        // was moments ago) alive — don't warn over a routine process restart.
        if (System.currentTimeMillis() - heartbeat < STALE_AFTER_MS) return

        runCatching {
            events.log(
                GuardEvent.Type.INFO,
                "Protection was stopped by the system while guarding was on (likely battery optimization). Restarting.",
            )
        }
        runCatching { GuardController.start(context) }
        notifyInterrupted()
    }

    private fun notifyInterrupted() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection warnings", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        // The policy-safe route: open the system's battery-optimization list (the direct request
        // permission is restricted on Play), where the user can set Guardia to "Unrestricted".
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val pi = PendingIntent.getActivity(context, 3, intent, PendingIntent.FLAG_IMMUTABLE)
        val text = "Your phone's battery manager stopped Guardia while guarding was on. " +
            "Protection has been restarted. To prevent this, set Guardia to \"Unrestricted\" battery use."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Protection was interrupted")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        // Same channel as GuardService's protection warnings so they group in system settings.
        const val CHANNEL_ID = "guardia_warnings"
        const val NOTIFICATION_ID = 1008
        /** Don't flag reboots — the BootReceiver's re-arm owns that window. */
        const val BOOT_GRACE_MS = 10 * 60_000L
        /** Two maintenance ticks: the service writes its heartbeat every minute. */
        const val STALE_AFTER_MS = 2 * 60_000L
    }
}

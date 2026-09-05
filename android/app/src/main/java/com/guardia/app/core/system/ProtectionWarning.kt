package com.guardia.app.core.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.guardia.app.MainActivity
import com.guardia.app.R

/**
 * The "you are not actually protected" notification.
 *
 * This is deliberately loud — same high-importance channel as the other protection warnings — and
 * it is the only way the user finds out on the occasion that matters most. The grant is revoked
 * while the app is closed (an update installs, Android drops the accessibility consent), so there
 * is no screen to put a banner on and nobody watching one. If it waited for the next launch it
 * would be telling the user about a gap they had already been walking around in for days.
 *
 * It cancels itself as soon as the grant comes back, so it can never linger as a false alarm.
 */
object ProtectionWarning {

    fun showAccessibilityRevoked(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    WARNING_CHANNEL_ID,
                    "Protection warnings",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "Updating Guardia switched off Android's app-detection permission, so App Lock " +
            "and per-app face checks are not running. Tap to turn it back on."
        val notification = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setContentTitle("App Lock is not active")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        // POST_NOTIFICATIONS may be denied; the dashboard carries the same warning either way.
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    fun clear(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    private const val WARNING_CHANNEL_ID = "guardia_warnings"
    private const val NOTIFICATION_ID = 1008
    private const val REQUEST_CODE = 8
}

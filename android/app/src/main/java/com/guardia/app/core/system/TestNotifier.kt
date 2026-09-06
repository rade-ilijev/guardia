package com.guardia.app.core.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guardia.app.R

/**
 * Posts non-intrusive feedback notifications used in Test Mode so the user can verify
 * face/voice recognition without the device actually locking.
 */
object TestNotifier {

    private const val CHANNEL_ID = "guardia_test"
    private const val FACE_NOTIF_ID = 2001
    private const val VOICE_NOTIF_ID = 2002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Test feedback",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Face/voice recognition results while testing" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun showFaceResult(context: Context, title: String, text: String) {
        post(context, FACE_NOTIF_ID, title, text)
    }

    fun showVoiceResult(context: Context, text: String) {
        post(context, VOICE_NOTIF_ID, "Voice safeword", text)
    }

    /**
     * Posts one result.
     *
     * Guarded twice over, like the Wi-Fi check. On Android 13+ notifications need a runtime grant
     * the user can refuse, and posting without it does nothing at all — so the permission is
     * checked and the work skipped rather than built and dropped. Anything the platform still
     * refuses is caught by runCatching. Lint cannot follow either guard, hence the annotation.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        // The previous 6s auto-timeout meant results flashed into the shade and vanished before
        // anyone noticed ("test mode shows nothing"). Keep the latest result visible for long
        // enough to actually read, stamp it with the check time, and re-alert on every update.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(60_000)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

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

    private fun post(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(6000)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

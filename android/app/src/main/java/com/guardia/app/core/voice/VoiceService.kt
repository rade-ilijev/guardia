package com.guardia.app.core.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import com.guardia.app.BuildConfig
import com.guardia.app.R
import com.guardia.app.core.guard.GuardController
import com.guardia.app.core.system.TestNotifier
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Microphone foreground service running Picovoice Porcupine wake-word detection.
 * One keyword starts guarding, another stops it - even when the app is closed.
 *
 * Defaults to two built-in keywords; replace with custom "guardia start/stop" .ppn
 * models (trained on the Picovoice console) for production. Requires a Picovoice
 * AccessKey in local.properties; without it the service no-ops gracefully.
 *
 * Speaker verification (Picovoice Eagle) is a planned enhancement so only the
 * owner's voice triggers the safeword.
 */
@AndroidEntryPoint
class VoiceService : LifecycleService() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var events: EventsRepository

    private var porcupineManager: PorcupineManager? = null
    private var micTracked = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground()
        startPorcupine()
        return START_STICKY
    }

    private fun startPorcupine() {
        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        if (accessKey.isBlank()) {
            Log.w(TAG, "No Picovoice AccessKey; voice safeword disabled")
            lifecycleScope.launch {
                events.log(GuardEvent.Type.INFO, "Voice safeword needs a Picovoice key")
            }
            stopSelf()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No microphone permission; voice safeword disabled")
            stopSelf()
            return
        }
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywords(
                    arrayOf(Porcupine.BuiltInKeyword.BLUEBERRY, Porcupine.BuiltInKeyword.GRASSHOPPER)
                )
                .setSensitivities(floatArrayOf(0.6f, 0.6f))
                .build(applicationContext) { keywordIndex -> onKeyword(keywordIndex) }
            porcupineManager?.start()
            if (!micTracked) { com.guardia.app.core.system.GuardiaCameraMic.enterMic(); micTracked = true }
        } catch (t: Throwable) {
            Log.e(TAG, "Porcupine init failed", t)
            stopSelf()
        }
    }

    private fun onKeyword(index: Int) {
        when (index) {
            KEYWORD_START -> {
                GuardController.start(applicationContext)
                TestNotifier.showVoiceResult(applicationContext, "Heard safeword - guarding started")
                logVoice("Guarding started by voice")
            }
            KEYWORD_STOP -> {
                GuardController.stop(applicationContext)
                TestNotifier.showVoiceResult(applicationContext, "Heard safeword - guarding stopped")
                logVoice("Guarding stopped by voice")
            }
        }
    }

    private fun logVoice(message: String) {
        lifecycleScope.launch {
            prefs.setGuardingEnabled(GuardController.isProtected)
            events.log(GuardEvent.Type.INFO, message)
        }
    }

    override fun onDestroy() {
        runCatching {
            porcupineManager?.stop()
            porcupineManager?.delete()
        }
        porcupineManager = null
        if (micTracked) { com.guardia.app.core.system.GuardiaCameraMic.exitMic(); micTracked = false }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening for your safeword")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMic) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice safeword", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "VoiceService"
        private const val CHANNEL_ID = "guardia_voice"
        private const val NOTIFICATION_ID = 1003
        private const val KEYWORD_START = 0
        private const val KEYWORD_STOP = 1
    }
}

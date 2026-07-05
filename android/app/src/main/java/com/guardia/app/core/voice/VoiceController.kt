package com.guardia.app.core.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Start/stop entry point for the voice safeword service. */
object VoiceController {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, VoiceService::class.java),
        )
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(Intent(context.applicationContext, VoiceService::class.java))
    }
}

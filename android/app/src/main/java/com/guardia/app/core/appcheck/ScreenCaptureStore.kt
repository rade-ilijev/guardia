package com.guardia.app.core.appcheck

import android.graphics.Bitmap

/**
 * Hand-off slot for the single screenshot captured just before a per-app face check launches.
 * The accessibility service (the only component that can screenshot other apps) writes the frame
 * here; [FaceCheckActivity] consumes it once to paint a blurred/frozen backdrop of the app.
 *
 * Bitmaps are too large to pass through an Intent, so we stash one here and clear it on read.
 */
object ScreenCaptureStore {
    @Volatile private var bitmap: Bitmap? = null
    @Volatile private var capturedAt = 0L

    fun set(bmp: Bitmap?) {
        bitmap = bmp
        capturedAt = System.currentTimeMillis()
    }

    /** Returns the captured frame if it's fresh, then clears the slot. */
    fun take(): Bitmap? {
        val now = System.currentTimeMillis()
        val bmp = bitmap
        bitmap = null
        return if (bmp != null && now - capturedAt <= MAX_AGE_MS) bmp else null
    }

    fun clear() { bitmap = null }

    private const val MAX_AGE_MS = 4000L
}

/** Lets [com.guardia.app.core.guard.AppTriggerManager] ask the accessibility service for a screenshot. */
fun interface ScreenshotProvider {
    fun capture(onResult: (Bitmap?) -> Unit)
}

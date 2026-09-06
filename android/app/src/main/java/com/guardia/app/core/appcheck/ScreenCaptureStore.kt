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
        // Replacing an unconsumed frame: the old one is nobody's now.
        bitmap?.takeIf { it !== bmp && !it.isRecycled }?.recycle()
        bitmap = bmp
        capturedAt = System.currentTimeMillis()
    }

    /**
     * Returns the captured frame if it's fresh, then clears the slot.
     *
     * A full-display ARGB_8888 screenshot is around 10 MB. A stale one is not going to be drawn,
     * so it is recycled here rather than left referenced until the next check happens to replace
     * it — which, for a per-app check the user may not trigger again for hours, is a long time to
     * carry a screenshot of their screen in memory.
     */
    fun take(): Bitmap? {
        val now = System.currentTimeMillis()
        val bmp = bitmap
        bitmap = null
        if (bmp == null) return null
        if (now - capturedAt <= MAX_AGE_MS) return bmp
        if (!bmp.isRecycled) bmp.recycle()
        return null
    }

    fun clear() {
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = null
    }

    private const val MAX_AGE_MS = 4000L
}

/** Lets [com.guardia.app.core.guard.AppTriggerManager] ask the accessibility service for a screenshot. */
fun interface ScreenshotProvider {
    fun capture(onResult: (Bitmap?) -> Unit)
}

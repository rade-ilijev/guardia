package com.guardia.app.core.system

import android.content.Context
import android.provider.Settings

/**
 * Cached lookup of the current default keyboard (IME) package. Foreground-window observers use it
 * to ignore the soft keyboard, which reports its own package in window-state events and must never
 * count as "switching apps" for App Lock or per-app face checks.
 */
object CurrentIme {

    @Volatile private var cached: String? = null
    @Volatile private var cachedAt = 0L

    fun packageName(context: Context): String? {
        val now = System.currentTimeMillis()
        if (now - cachedAt > CACHE_MS) {
            cached = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                    ?.substringBefore('/')
            }.getOrNull()
            cachedAt = now
        }
        return cached
    }

    private const val CACHE_MS = 10_000L
}

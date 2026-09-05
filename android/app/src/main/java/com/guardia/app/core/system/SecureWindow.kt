package com.guardia.app.core.system

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * Marks an Activity's window as secure: the OS then blocks screenshots, screen recording, and
 * capture by other apps' overlays/assistants while it is visible. Applied to every screen that
 * shows a PIN pad or intruder evidence so a screen-capturing malware (or a shoulder-surfing screen
 * recorder) can't lift them.
 */
fun markSecure(activity: Activity) = setSecure(activity, true)

/**
 * Sets or clears the secure flag.
 *
 * Clearing it is a real reduction in protection, so only [com.guardia.app.MainActivity] ever does,
 * and only because the user asked: the flag is what stops them capturing their own screen, which
 * they legitimately need to do for a support ticket, a bug report or a store listing. The windows
 * that appear *over other apps* — the PIN gates and the per-app face check — never call this and
 * stay secure whatever the setting says. Those are the ones a hostile screen recorder would be
 * waiting for, and their content is a PIN being typed.
 */
fun setSecure(activity: Activity, secure: Boolean) {
    if (secure) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * A synchronous mirror of the "allow screen capture" preference.
 *
 * The preference itself lives in DataStore, which can only be read from a coroutine. That is a
 * problem here and nowhere else, because FLAG_SECURE has to be decided *before* the window is
 * created: setting it afterwards works on paper, but clearing it on a window that already exists
 * is honoured inconsistently across OEM builds, and on the ones that ignore it the user flips the
 * switch and nothing happens. Deciding at creation time is the only version that behaves the same
 * everywhere.
 *
 * So the value is mirrored into SharedPreferences, which reads synchronously. DataStore stays the
 * source of truth — this is a cache written whenever the real value is observed, never the thing
 * the settings screen edits.
 */
object CaptureFlag {

    // Same default as the DataStore value it mirrors: allowed on debug, blocked on release.
    // Without this the first launch after install would still come up secure, and the user would
    // have to background the app once before it took effect.
    fun isAllowed(context: Context): Boolean =
        prefs(context).getBoolean(KEY, com.guardia.app.BuildConfig.DEBUG)

    fun setAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY, allowed).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "guardia_window_flags"
    private const val KEY = "allow_screen_capture"
}

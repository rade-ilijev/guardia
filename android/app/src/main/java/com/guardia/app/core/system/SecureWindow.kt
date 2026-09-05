package com.guardia.app.core.system

import android.app.Activity
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
 *
 * The flag can be flipped on a live window; the change applies from the next frame.
 */
fun setSecure(activity: Activity, secure: Boolean) {
    if (secure) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

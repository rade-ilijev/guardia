package com.guardia.app.core.system

import android.app.Activity
import android.view.WindowManager

/**
 * Marks an Activity's window as secure: the OS then blocks screenshots, screen recording, and
 * capture by other apps' overlays/assistants while it is visible. Applied to every screen that
 * shows a PIN pad or intruder evidence so a screen-capturing malware (or a shoulder-surfing screen
 * recorder) can't lift them.
 */
fun markSecure(activity: Activity) {
    activity.window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE,
    )
}

package com.guardia.app.core.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.guardia.app.core.alerts.SmsReceiver

/**
 * Keeps high-risk manifest components disabled until the user explicitly opts in.
 * Static scanners (e.g. Play Protect on sideloaded APKs) weigh active SMS receivers
 * heavily; we only enable [SmsReceiver] when Find-my-phone is turned on.
 */
object SensitiveComponents {

    fun setSmsReceiverEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SmsReceiver::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}

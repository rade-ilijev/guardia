package com.guardia.app.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardia.app.core.guard.GuardController
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms guarding after reboot if the user had it enabled.
 *
 * Platform note: a camera foreground service generally cannot be started from
 * BOOT_COMPLETED on newer Android. We best-effort restore the armed state; the
 * camera is armed once the device is unlocked/interactive.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (prefs.guardingEnabled.first()) {
                    runCatching { GuardController.start(context) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

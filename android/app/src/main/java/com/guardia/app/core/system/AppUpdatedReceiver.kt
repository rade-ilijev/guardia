package com.guardia.app.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires when Guardia itself has just been updated, and checks whether the update cost the user
 * their protection.
 *
 * Android re-applies its restricted-settings block to a sideloaded app on every update, which
 * silently switches the accessibility service back off. Everything that depends on knowing the
 * foreground app — App Lock and per-app face checks — then stops, while the app's own settings
 * still say they are on. ACTION_MY_PACKAGE_REPLACED is the exact moment that happens, and it is
 * the only chance to tell the user before they next open the app and assume all is well.
 *
 * Nothing is warned about if nothing depended on it: a user with no locked apps and no per-app
 * triggers has lost nothing, and a notification would just be noise.
 */
@AndroidEntryPoint
class AppUpdatedReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val depends = prefs.lockedApps.first().isNotEmpty() ||
                    prefs.triggerApps.first().isNotEmpty()
                if (depends && !AccessibilityAccess.refresh(app)) {
                    ProtectionWarning.showAccessibilityRevoked(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

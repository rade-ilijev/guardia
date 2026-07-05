package com.guardia.app.core.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Device Admin: enables lockNow() (lock on failed check) and onPasswordFailed() (wrong-unlock
 * intruder selfie hook), and reacts when someone tries to remove admin (tamper response).
 */
@AndroidEntryPoint
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject lateinit var tamperResponder: TamperResponder
    @Inject lateinit var prefs: AppPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        val pending = goAsync()
        scope.launch {
            try {
                // Only capture once the configured number of consecutive wrong unlocks is reached,
                // so a single fat-finger doesn't fire the camera. threshold 1 = every failure.
                val count = prefs.recordWrongUnlock()
                val threshold = prefs.wrongUnlockThreshold.first().coerceAtLeast(1)
                if (count >= threshold) {
                    prefs.resetWrongUnlocks()
                    if (prefs.captureIntruders.first()) {
                        runCatching { IntruderCaptureService.start(context, "Wrong device unlock") }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        val pending = goAsync()
        scope.launch {
            try {
                prefs.resetWrongUnlocks()
            } finally {
                pending.finish()
            }
        }
    }

    /** Shown by the system when the user tries to deactivate Guardia's device admin — a deterrent. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Disabling this also disables Guardia's ability to lock your device when an unauthorized " +
            "person is detected. If guarding is on, this attempt will be recorded."

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        runCatching { tamperResponder.onTamper("Device admin was removed") }
    }
}

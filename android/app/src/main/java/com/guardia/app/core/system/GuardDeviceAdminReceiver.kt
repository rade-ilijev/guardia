package com.guardia.app.core.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Device Admin: enables lockNow() (lock on failed check) and onPasswordFailed() (wrong-unlock
 * intruder selfie hook), and reacts when someone tries to remove admin (tamper response).
 */
@AndroidEntryPoint
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject lateinit var tamperResponder: TamperResponder

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        // Best-effort: grab a front-camera frame of whoever entered the wrong PIN.
        runCatching { IntruderCaptureService.start(context, "Wrong device unlock") }
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

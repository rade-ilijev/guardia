package com.guardia.app.core.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Thin wrapper over Device Admin used to lock the screen on a failed check. */
object DeviceAdminManager {

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun component(context: Context) =
        ComponentName(context, GuardDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean =
        dpm(context).isAdminActive(component(context))

    /** True if at least one lock mechanism is available: Device Admin or the accessibility service. */
    fun canLock(context: Context): Boolean =
        isAdminActive(context) || GuardAccessibilityService.isConnected()

    /** Intent that opens the system "activate device admin" screen. */
    fun enableIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Guardia uses device admin to lock the screen instantly when an unauthorized person is detected.",
            )
        }

    /**
     * Locks the device immediately, returning whether a lock was actually issued. Tries Device Admin
     * first (the primary path); if admin isn't active or the call fails, falls back to the
     * accessibility service's global lock action (works on API 28+ without Device Admin). Having two
     * independent mechanisms greatly improves lock reliability across OEMs and setup states.
     */
    fun lockNow(context: Context): Boolean {
        if (isAdminActive(context)) {
            val ok = runCatching { dpm(context).lockNow() }.isSuccess
            if (ok) return true
        }
        // Fallback: accessibility global lock (no Device Admin required).
        return GuardAccessibilityService.lockScreen()
    }
}

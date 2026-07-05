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

    /** Intent that opens the system "activate device admin" screen. */
    fun enableIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Guardia uses device admin to lock the screen instantly when an unauthorized person is detected.",
            )
        }

    /** Locks the device immediately. No-op if admin is not active. */
    fun lockNow(context: Context) {
        if (isAdminActive(context)) {
            runCatching { dpm(context).lockNow() }
        }
    }
}

package com.guardia.app.core.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

/**
 * Reads the connected Wi-Fi network's SSID for the trusted-network feature. Android gates the
 * SSID behind location permission (knowing the network reveals where you are), so this returns
 * null without it — callers treat null as "not on a trusted network" and the settings UI explains
 * the requirement.
 */
object WifiTrust {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The current SSID (unquoted), or null when unknown/not on Wi-Fi/permission missing. */
    fun currentSsid(context: Context): String? {
        if (!hasLocationPermission(context)) return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        @Suppress("DEPRECATION")
        val raw = runCatching { wifi.connectionInfo?.ssid }.getOrNull() ?: return null
        val ssid = raw.removePrefix("\"").removeSuffix("\"")
        return ssid.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    /** True when connected to one of [trusted] right now. */
    fun onTrustedNetwork(context: Context, trusted: Set<String>): Boolean {
        if (trusted.isEmpty()) return false
        val current = currentSsid(context) ?: return false
        return current in trusted
    }
}

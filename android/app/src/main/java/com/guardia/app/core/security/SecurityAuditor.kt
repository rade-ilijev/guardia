package com.guardia.app.core.security

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device privacy audit of the other apps installed on this phone. Uses only PackageManager and
 * the public system-service lists — nothing is read from inside other apps, and nothing leaves the
 * device. Each app is scored by the sensitive capabilities it actually holds, and those are
 * translated into plain-language risks the owner can act on.
 */
@Singleton
class SecurityAuditor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    enum class Risk { HIGH, MEDIUM, LOW }

    /**
     * What kind of capability this is, independent of how it is worded.
     *
     * The UI picks an icon from this rather than from [Capability.label]: the label is the user's
     * wording, it is translated, and matching a picture to a translated sentence breaks the moment
     * anyone edits a string.
     */
    enum class CapabilityKind {
        SCREEN, NOTIFICATIONS, DEVICE_ADMIN, MICROPHONE, CAMERA, SMS_READ, SMS_SEND,
        CALL_LOG, LOCATION, OVERLAY, CONTACTS, CALENDAR, APP_LIST, INSTALL_APPS,
    }

    /** One sensitive capability an app currently holds, in the user's words. */
    data class Capability(val label: String, val high: Boolean, val kind: CapabilityKind)

    /**
     * Marked `@Immutable` for Compose: it holds a `List`, and Compose treats every `List` as unstable
     * because the interface allows a mutable implementation. Without the annotation, any composable
     * reading this state is re-run on *every* recomposition of its parent, even when the state itself
     * has not changed. The contents genuinely are never mutated after construction, so the promise is
     * safe to make — and it is what lets Compose skip the subtree.
     */
    @Immutable
    data class AppAudit(
        val packageName: String,
        val name: String,
        val isSystem: Boolean,
        val capabilities: List<Capability>,
        val hasInternet: Boolean,
        val risk: Risk,
        /** Higher = riskier; used only for ordering. */
        val score: Int,
    )

    /**
     * Audits every launchable/user-visible app. Ordered riskiest first. Guardia itself and pure
     * system components with no sensitive capability are excluded so the list stays actionable.
     */
    fun audit(): List<AppAudit> {
        val pm = context.packageManager
        val accessibility = enabledAccessibilityPackages()
        val notifListeners = enabledNotificationListenerPackages()
        val admins = activeDeviceAdminPackages()

        // Collated, not lowercased: sorting user-visible names by their lowercase form puts every
        // accented letter after Z, so a German or Swedish user's app list looks broken to them.
        val collator = java.text.Collator.getInstance()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages.mapNotNull { appInfo ->
            val pkg = appInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val audit = auditApp(pm, appInfo, accessibility, notifListeners, admins)
            // Drop apps that hold nothing sensitive — they'd just be noise.
            audit.takeIf { it.capabilities.isNotEmpty() }
        }.sortedWith(
            compareByDescending<AppAudit> { it.score }
                .then(Comparator { a, b -> collator.compare(a.name, b.name) })
        )
    }

    private fun auditApp(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        accessibility: Set<String>,
        notifListeners: Set<String>,
        admins: Set<String>,
    ): AppAudit {
        val pkg = appInfo.packageName
        val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val granted = grantedPermissions(pm, pkg)
        val caps = mutableListOf<Capability>()

        fun cap(label: String, high: Boolean, kind: CapabilityKind, condition: Boolean) {
            if (condition) caps.add(Capability(label, high, kind))
        }

        val hasInternet = granted.contains(Manifest.permission.INTERNET) ||
            requestsPermission(pm, pkg, Manifest.permission.INTERNET)

        cap("Can see your screen", high = true, CapabilityKind.SCREEN, pkg in accessibility)
        cap("Can read your notifications", high = true, CapabilityKind.NOTIFICATIONS, pkg in notifListeners)
        cap("Can lock or wipe this device", high = true, CapabilityKind.DEVICE_ADMIN, pkg in admins)
        cap("Can record audio", high = true, CapabilityKind.MICROPHONE, granted.contains(Manifest.permission.RECORD_AUDIO))
        cap("Can use the camera", high = true, CapabilityKind.CAMERA, granted.contains(Manifest.permission.CAMERA))
        cap("Can read your texts", high = true, CapabilityKind.SMS_READ, granted.contains(Manifest.permission.READ_SMS))
        cap("Can send texts", high = true, CapabilityKind.SMS_SEND, granted.contains(Manifest.permission.SEND_SMS))
        cap("Can read call logs", high = true, CapabilityKind.CALL_LOG, granted.contains(Manifest.permission.READ_CALL_LOG))
        cap("Knows your precise location", high = true, CapabilityKind.LOCATION, granted.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        cap("Can draw over other apps", high = true, CapabilityKind.OVERLAY, granted.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
        cap("Can read your contacts", high = false, CapabilityKind.CONTACTS, granted.contains(Manifest.permission.READ_CONTACTS))
        cap("Can read your calendar", high = false, CapabilityKind.CALENDAR, granted.contains(Manifest.permission.READ_CALENDAR))
        cap("Sees every app you install", high = false, CapabilityKind.APP_LIST, requestsPermission(pm, pkg, "android.permission.QUERY_ALL_PACKAGES"))
        cap("Can install other apps", high = false, CapabilityKind.INSTALL_APPS, requestsPermission(pm, pkg, Manifest.permission.REQUEST_INSTALL_PACKAGES))

        var score: Int = caps.fold(0) { acc, cap -> acc + if (cap.high) 10 else 3 }
        // The exfiltration multiplier: a sensitive capability plus internet is what turns "an app
        // that can listen" into "an app that can listen and send it somewhere."
        if (hasInternet && caps.any { it.high }) score += 15
        // A user-installed app with these powers is more noteworthy than a preloaded system one.
        if (!isSystem) score += 5

        val risk = when {
            caps.any { it.high } && hasInternet -> Risk.HIGH
            caps.any { it.high } -> Risk.MEDIUM
            else -> Risk.LOW
        }
        return AppAudit(pkg, label(pm, appInfo), isSystem, caps, hasInternet, risk, score)
    }

    private fun label(pm: PackageManager, appInfo: ApplicationInfo): String =
        runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(appInfo.packageName)

    /** Permissions the package has been *granted* (runtime) or holds (install-time). */
    private fun grantedPermissions(pm: PackageManager, pkg: String): Set<String> = runCatching {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: IntArray(requested.size)
        buildSet {
            requested.forEachIndexed { i, perm ->
                val isGranted = i < flags.size &&
                    (flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                if (isGranted) add(perm)
            }
        }
    }.getOrDefault(emptySet())

    private fun requestsPermission(pm: PackageManager, pkg: String, perm: String): Boolean = runCatching {
        pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions?.contains(perm) == true
    }.getOrDefault(false)

    private fun enabledAccessibilityPackages(): Set<String> =
        packagesFromSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun enabledNotificationListenerPackages(): Set<String> =
        packagesFromSecureSetting("enabled_notification_listeners")

    /** Parses a colon-separated list of "pkg/Service" component strings into package names. */
    private fun packagesFromSecureSetting(key: String): Set<String> = runCatching {
        Settings.Secure.getString(context.contentResolver, key)
            ?.split(':')
            ?.mapNotNull { it.substringBefore('/').takeIf(String::isNotBlank) }
            ?.toSet()
            ?: emptySet()
    }.getOrDefault(emptySet())

    private fun activeDeviceAdminPackages(): Set<String> = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    }.getOrDefault(emptySet())
}

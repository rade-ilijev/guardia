package com.guardia.app.core.system

import android.content.Context
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user currently has [GuardAccessibilityService] switched on.
 *
 * Android gives an app no way to grant itself accessibility access — that is the whole point of
 * the permission, and Guardia is exactly the sort of app it exists to gate. What the app *can* do
 * is notice when the grant has gone, because it goes more often than users expect: Android
 * re-applies its "restricted settings" block to a sideloaded app on **every** update, and a change
 * to a service's declared capabilities revokes consent on its own.
 *
 * Before this existed, that revocation was silent. App Lock's switch still read "on", the guard
 * still reported PROTECTED, and no app was actually being locked. A security app that quietly
 * stops securing is worse than one that plainly says it cannot.
 *
 * The connected-instance check in [GuardAccessibilityService.isConnected] answers a different
 * question — whether the service is bound *right now*, in this process — and is null for a moment
 * after every process start. This reads the user's actual choice, so it is stable and correct
 * before anything has bound.
 */
object AccessibilityAccess {

    /**
     * Starts optimistic on purpose. This flow drives a "you are not protected" alert, and the
     * honest default before anything has been read is *don't know* — showing the alarm in that gap
     * would flash a false warning at every launch for users whose grant is perfectly fine. A real
     * revocation is reported one [refresh] later, which happens immediately on resume and on
     * service start; a false alarm has no such cure, and an alert that cries wolf is one the user
     * learns to swipe away.
     */
    private val _enabled = MutableStateFlow(true)

    /** Last known state. Optimistic until the first [refresh]; observers get every later change. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Re-reads the system setting and publishes the result to [enabled]. */
    fun refresh(context: Context): Boolean {
        val on = isEnabled(context)
        _enabled.value = on
        return on
    }

    fun isEnabled(context: Context): Boolean = runCatching {
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        listsService(setting, context.packageName)
    }.getOrDefault(false)

    /**
     * True if [setting] — the platform's colon-separated list of enabled services — names this
     * app's guard service.
     *
     * Split out and parsed properly rather than matched with `contains`, for two reasons. The
     * flavours have different application IDs (`com.guardia.app` and `com.guardia.app.full`) while
     * sharing one class name, so a substring test on the *class* matches the sibling build, and a
     * substring test on `com.guardia.app` matches `com.guardia.app.full` as a prefix. And the
     * platform may store either the short form (`pkg/.core.system.GuardAccessibilityService`) or
     * the fully-qualified one, depending on how it was written.
     */
    @VisibleForTesting
    fun listsService(setting: String?, packageName: String): Boolean {
        if (setting.isNullOrBlank()) return false
        return setting.split(':').any { entry ->
            val trimmed = entry.trim()
            val slash = trimmed.indexOf('/')
            if (slash <= 0) return@any false
            val pkg = trimmed.substring(0, slash)
            val cls = trimmed.substring(slash + 1)
            pkg == packageName && cls.substringAfterLast('.') == SERVICE_CLASS_SIMPLE_NAME
        }
    }

    /**
     * Held as a literal rather than `GuardAccessibilityService::class.java.simpleName` so this
     * function stays plain Kotlin: naming the class would load it, and its `AccessibilityService`
     * superclass is a stub on the unit-test classpath. [AccessibilityAccessTest] is the check that
     * this literal still matches the class.
     */
    private const val SERVICE_CLASS_SIMPLE_NAME = "GuardAccessibilityService"
}

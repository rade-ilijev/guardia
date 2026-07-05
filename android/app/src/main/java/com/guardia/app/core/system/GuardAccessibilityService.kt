package com.guardia.app.core.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.guardia.app.core.applock.AppLockManager
import com.guardia.app.core.appcheck.ScreenshotProvider
import com.guardia.app.core.guard.AppTriggerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Detects the current foreground app and forwards it to [AppLockManager] (per-app PIN gate)
 * and [AppTriggerManager] (immediate face check on app open). Collects no content and sends
 * nothing off-device.
 *
 * Also acts as the [ScreenshotProvider] for [AppTriggerManager]: when a guarded app opens with
 * the "blur" or "freeze" check style, it grabs a single frame of the app so the check screen can
 * render the app content blurred/frozen instead of an opaque spinner.
 */
@AndroidEntryPoint
class GuardAccessibilityService : AccessibilityService() {

    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var appTriggerManager: AppTriggerManager
    @Inject lateinit var tamperResponder: TamperResponder

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // The Play build ships a minimal accessibility config with no screen-capture capability, so
        // never offer a screenshot provider there (per-app checks fall back to the opaque style).
        if (!com.guardia.app.BuildConfig.PLAY_BUILD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appTriggerManager.screenshotProvider = ScreenshotProvider { onResult -> takeAppScreenshot(onResult) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val foregroundPackage = event.packageName?.toString() ?: return
            // Ignore system UI / input method noise.
            if (foregroundPackage.isBlank()) return
            appLockManager.onForeground(foregroundPackage)
            appTriggerManager.onForeground(foregroundPackage)
        }
    }

    override fun onInterrupt() {
        // No-op.
    }

    companion object {
        @Volatile private var instance: GuardAccessibilityService? = null

        /**
         * Sends the device to the home screen, reliably backgrounding the current (guarded) app.
         * Returns false if the accessibility service isn't connected, so the caller can fall back to
         * launching a HOME intent. More dependable than a HOME intent on some OEM launchers.
         */
        fun goHome(): Boolean {
            val svc = instance ?: return false
            return runCatching { svc.performGlobalAction(GLOBAL_ACTION_HOME) }.getOrDefault(false)
        }

        /**
         * Locks the screen via the accessibility global action (API 28+). A second, Device-Admin-free
         * lock path so guarding can still lock when admin isn't granted, provided this service is on.
         * Returns false if the service isn't connected or the platform is too old.
         */
        fun lockScreen(): Boolean {
            val svc = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return runCatching { svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }.getOrDefault(false)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        appTriggerManager.screenshotProvider = null
        // Turning the accessibility service off silently disables App Lock and per-app face checks.
        // If the user intended to be protected, treat it as tampering.
        runCatching { tamperResponder.onTamper("Accessibility service was turned off") }
        return super.onUnbind(intent)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun takeAppScreenshot(onResult: (Bitmap?) -> Unit) {
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bmp = runCatching {
                            val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                            // Copy to a software bitmap we fully own, then release the buffer.
                            hw?.copy(Bitmap.Config.ARGB_8888, false)
                        }.getOrNull()
                        runCatching { screenshot.hardwareBuffer.close() }
                        onResult(bmp)
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null)
                    }
                },
            )
        }.onFailure { onResult(null) }
    }
}

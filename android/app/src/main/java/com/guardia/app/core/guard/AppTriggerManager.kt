package com.guardia.app.core.guard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.guardia.app.core.appcheck.FaceCheckActivity
import com.guardia.app.core.appcheck.OverlayController
import com.guardia.app.core.appcheck.ScreenCaptureStore
import com.guardia.app.core.appcheck.ScreenshotProvider
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When one of the user-chosen "trigger" apps comes to the foreground, Guardia shows a brief
 * full-screen face check ([FaceCheckActivity]) over it. If the owner isn't recognized the app is
 * closed (sent home) instead of locking the whole device.
 *
 * A trigger app is checked **once per visit**: after it passes it stays cleared until the user
 * actually switches to a different real app (or the screen turns off). Transient windows that pop
 * up *inside* an app — the soft keyboard, system UI, dialogs, our own overlay — are ignored so the
 * check doesn't fire again while you comment, open a chat, etc.
 */
@Singleton
class AppTriggerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlayController: OverlayController,
    prefs: AppPreferences,
) {
    @Volatile private var triggerPackages: Set<String> = emptySet()
    @Volatile private var lastRealPackage: String? = null
    @Volatile private var passedPackage: String? = null
    /** Per-app check style: 0 = loading spinner, 1 = blur, 2 = freeze. */
    @Volatile private var checkStyle = 0

    /** Set by the accessibility service so blur/freeze styles can grab a frame of the app. */
    @Volatile var screenshotProvider: ScreenshotProvider? = null

    /**
     * True from the moment a per-app [FaceCheckActivity] is launched until it is destroyed. The
     * background guard loop reads this to avoid grabbing the (single, process-wide) front camera
     * out from under an in-flight per-app check.
     */
    @Volatile var checkInProgress = false

    @Volatile private var cachedIme: String? = null
    @Volatile private var imeCachedAt = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { prefs.triggerApps.collect { triggerPackages = it } }
        scope.launch { prefs.appCheckStyle.collect { checkStyle = it } }
    }

    fun onForeground(pkg: String) {
        if (pkg.isBlank() || isTransient(pkg)) return
        // Same real app still in front (including the app we already passed) -> nothing to do.
        if (pkg == lastRealPackage) return
        lastRealPackage = pkg
        // Moved to a genuinely different app/home: any previous pass no longer applies.
        passedPackage = null
        if (GuardController.isProtected && triggerPackages.contains(pkg)) {
            launchCheck(pkg)
        }
    }

    /** Called by [FaceCheckActivity] once the owner has been verified for [pkg]. */
    fun markPassed(pkg: String) {
        passedPackage = pkg
        lastRealPackage = pkg
    }

    fun onScreenOff() {
        passedPackage = null
        lastRealPackage = null
    }

    /** Windows that appear *over* the current app and must not count as switching apps. */
    private fun isTransient(pkg: String): Boolean {
        if (pkg == context.packageName) return true
        if (pkg == "com.android.systemui") return true
        if (pkg == passedPackage) return true
        return pkg == currentImePackage()
    }

    private fun currentImePackage(): String? {
        val now = System.currentTimeMillis()
        if (now - imeCachedAt > IME_CACHE_MS) {
            cachedIme = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                    ?.substringBefore('/')
            }.getOrNull()
            imeCachedAt = now
        }
        return cachedIme
    }

    private fun launchCheck(pkg: String) {
        val provider = screenshotProvider
        // Blur/freeze styles render the actual app content, so capture a frame of it first and
        // launch over it (no opaque cover). Loading style hides the app behind an instant cover.
        if (checkStyle != 0 && provider != null) {
            ScreenCaptureStore.clear()
            provider.capture { bmp ->
                ScreenCaptureStore.set(bmp)
                // If we couldn't grab a frame, fall back to the opaque cover so nothing flashes.
                if (bmp == null) overlayController.showCover()
                startCheckActivity(pkg)
            }
        } else {
            overlayController.showCover()
            startCheckActivity(pkg)
        }
    }

    private fun startCheckActivity(pkg: String) {
        checkInProgress = true
        val intent = Intent(context, FaceCheckActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(FaceCheckActivity.EXTRA_PACKAGE, pkg)
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            checkInProgress = false
            overlayController.hideCover()
        }
    }

    private companion object {
        const val IME_CACHE_MS = 10_000L
    }
}

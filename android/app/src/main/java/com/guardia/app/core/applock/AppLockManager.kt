package com.guardia.app.core.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.guardia.app.core.system.CurrentIme
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which apps are locked and decides whether the unlock screen must be shown when an
 * app comes to the foreground. A package stays unlocked only while it remains foreground;
 * navigating away re-arms the lock.
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    prefs: AppPreferences,
) {
    @Volatile private var lockedPackages: Set<String> = emptySet()
    @Volatile private var unlockedPackage: String? = null
    @Volatile private var lastForeground: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { prefs.lockedApps.collect { lockedPackages = it } }
        // Screen-off must re-arm every lock. App Lock runs off the accessibility service (with or
        // without guarding), so this singleton listens for itself rather than relying on
        // GuardService's receiver.
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    val hasLockedApps: Boolean get() = lockedPackages.isNotEmpty()

    /** Called by the accessibility service whenever the foreground package changes. */
    fun onForeground(pkg: String) {
        // The keyboard, System UI (shade/recents surface reuse) and our own gate appear *over* the
        // current app; treating them as an app switch would re-lock mid-use (e.g. while typing).
        if (isTransient(pkg)) return
        // Leaving a previously-unlocked app re-arms its lock.
        if (unlockedPackage != null && pkg != unlockedPackage) {
            unlockedPackage = null
        }
        if (pkg != lastForeground && lockedPackages.contains(pkg) && unlockedPackage != pkg) {
            launchLock(pkg)
        }
        lastForeground = pkg
    }

    /** Windows that appear over the current app and must not count as switching apps. */
    private fun isTransient(pkg: String): Boolean {
        if (pkg == context.packageName) return true
        if (pkg == "com.android.systemui") return true
        return pkg == CurrentIme.packageName(context)
    }

    fun markUnlocked(pkg: String) {
        unlockedPackage = pkg
        lastForeground = pkg
    }

    fun onScreenOff() {
        unlockedPackage = null
        lastForeground = null
    }

    private fun launchLock(pkg: String) {
        val intent = Intent(context, AppLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AppLockActivity.EXTRA_PACKAGE, pkg)
        }
        runCatching { context.startActivity(intent) }
    }
}

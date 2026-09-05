package com.guardia.app

import android.app.Application
import com.guardia.app.core.system.CrashLogger
import com.guardia.app.core.system.SensitiveComponents
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point. Hilt dependency graph root.
 */
@HiltAndroidApp
class GuardiaApp : Application() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var guardWatchdog: com.guardia.app.core.system.GuardWatchdog

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Install the crash handler early; it only writes when the user has opted in.
        CrashLogger.install(this)
        appScope.launch { prefs.crashLogEnabled.collectLatest { CrashLogger.enabled = it } }
        // If the OS killed the guard while guarding was on, restart it and tell the owner.
        appScope.launch(Dispatchers.Default) { runCatching { guardWatchdog.checkOnProcessStart() } }
        appScope.launch(Dispatchers.IO) {
            SensitiveComponents.setSmsReceiverEnabled(
                this@GuardiaApp,
                prefs.findMyPhoneEnabled.first(),
            )
            // Evidence export decrypts a capture to cache/shared for a share sheet. If the process
            // died mid-share, a plaintext copy could linger; clear it on startup so decrypted
            // intruder photos never outlive the share.
            runCatching {
                java.io.File(cacheDir, "shared").listFiles()?.forEach { it.delete() }
            }
        }
    }
}

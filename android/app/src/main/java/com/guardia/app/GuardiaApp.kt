package com.guardia.app

import android.app.Application
import com.guardia.app.core.system.SensitiveComponents
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point. Hilt dependency graph root.
 */
@HiltAndroidApp
class GuardiaApp : Application() {

    @Inject lateinit var prefs: AppPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        appScope.launch(Dispatchers.IO) {
            SensitiveComponents.setSmsReceiverEnabled(
                this@GuardiaApp,
                prefs.findMyPhoneEnabled.first(),
            )
        }
    }
}

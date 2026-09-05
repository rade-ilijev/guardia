package com.guardia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.guardia.app.ui.GuardiaRoot
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.guardia.app.ui.AppViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.guardia.app.ui.theme.GuardiaAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Same instance GuardiaRoot's hiltViewModel() resolves to: this activity is the nearest
    // ViewModelStoreOwner, so the gate the lifecycle drives is the gate the UI renders.
    private val appViewModel: AppViewModel by viewModels()

    @javax.inject.Inject lateinit var prefs: com.guardia.app.data.AppPreferences

    override fun onStart() {
        super.onStart()
        appViewModel.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onAppStopped(changingConfigurations = isChangingConfigurations)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Secure first, relax second. The window is marked before anything is drawn, so a launch
        // never shows a capturable frame while the preference is still being read off disk; if the
        // user has allowed capture, the flag is cleared a moment later. Doing it the other way
        // round would leak exactly one screenshot's worth of whatever was on screen at start.
        com.guardia.app.core.system.markSecure(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.allowScreenCapture.collect { allowed ->
                    com.guardia.app.core.system.setSecure(this@MainActivity, !allowed)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            GuardiaAppTheme {
                GuardiaRoot()
            }
        }
    }
}

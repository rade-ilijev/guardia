package com.guardia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.guardia.app.core.system.CaptureFlag
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

    /** What the current window was actually built with, so a no-op change never recreates. */
    private var appliedCaptureAllowed: Boolean? = null

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
        // Decided before the window exists, from the synchronous mirror of the preference.
        // Clearing FLAG_SECURE on a window that has already been created is honoured
        // inconsistently across OEM builds, so the flag is only ever *set at creation*: flip the
        // switch and the activity is recreated below rather than patched in place.
        val captureAllowed = CaptureFlag.isAllowed(this)
        appliedCaptureAllowed = captureAllowed
        com.guardia.app.core.system.setSecure(this, !captureAllowed)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.allowScreenCapture.collect { allowed ->
                    // DataStore stays the source of truth; this keeps the synchronous copy that
                    // the next onCreate reads honest.
                    CaptureFlag.setAllowed(this@MainActivity, allowed)
                    if (allowed != appliedCaptureAllowed) {
                        appliedCaptureAllowed = allowed
                        // Rebuild the window with the right flag. Cheap, and the only way the
                        // change is guaranteed to take on every device.
                        recreate()
                    }
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

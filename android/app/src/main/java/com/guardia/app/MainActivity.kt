package com.guardia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.guardia.app.ui.GuardiaRoot
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.guardia.app.ui.AppViewModel
import com.guardia.app.ui.theme.GuardiaAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Same instance GuardiaRoot's hiltViewModel() resolves to: this activity is the nearest
    // ViewModelStoreOwner, so the gate the lifecycle drives is the gate the UI renders.
    private val appViewModel: AppViewModel by viewModels()

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
        // The main UI shows PIN entry and decrypted intruder evidence — keep it off screenshots,
        // screen recordings, and other apps' capture overlays.
        com.guardia.app.core.system.markSecure(this)
        enableEdgeToEdge()
        setContent {
            GuardiaAppTheme {
                GuardiaRoot()
            }
        }
    }
}

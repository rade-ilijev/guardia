package com.guardia.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.main.MainScreen
import com.guardia.app.ui.screens.decoy.DecoyScreen
import com.guardia.app.ui.screens.lock.LockScreen
import com.guardia.app.ui.screens.onboarding.OnboardingScreen

@Composable
fun GuardiaRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val gate by appViewModel.gate.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            // One shared ambient console backdrop behind every screen — except the decoy,
            // which must look like an ordinary, boring app.
            if (gate != AppGate.DECOY) com.guardia.app.ui.components.GuardiaBackdrop()
            when (gate) {
                AppGate.LOADING -> Loading()
                AppGate.ONBOARDING -> OnboardingScreen(onComplete = appViewModel::onOnboardingComplete)
                AppGate.LOCKED -> LockScreen(
                    onUnlocked = appViewModel::onUnlocked,
                    onDecoy = appViewModel::onDecoy,
                )
                AppGate.UNLOCKED -> MainScreen(onLock = appViewModel::lock)
                AppGate.DECOY -> DecoyScreen()
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

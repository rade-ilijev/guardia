package com.guardia.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import com.guardia.app.ui.components.GuardiaBackdrop
import com.guardia.app.ui.main.MainScreen
import com.guardia.app.ui.screens.decoy.DecoyScreen
import com.guardia.app.ui.screens.lock.LockScreen
import com.guardia.app.ui.screens.onboarding.OnboardingScreen

@Composable
fun GuardiaRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val gate by appViewModel.gate.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            // One shared ambient console backdrop stays mounted behind every gate so the transition
            // between them never flashes the raw background — except the decoy, which must look
            // like an ordinary, boring app.
            if (gate != AppGate.DECOY) GuardiaBackdrop()

            // A gentle fade + scale between gates hides the cost of the next screen's first
            // composition (e.g. the dashboard spinning up its animations), which is what made the
            // PIN unlock feel glitchy when the swap happened in a single frame.
            AnimatedContent(
                targetState = gate,
                transitionSpec = {
                    (fadeIn(tween(320)) + scaleIn(tween(360), initialScale = 0.97f)) togetherWith
                        fadeOut(tween(200)) + scaleOut(tween(240), targetScale = 1.02f)
                },
                label = "gate",
            ) { current ->
                when (current) {
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
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

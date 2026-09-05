package com.guardia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.components.LocalMotionOverride
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Appearance preferences: read by [GuardiaAppTheme] at every activity root, and written by the
 * Appearance settings screen. One class for both so a change made in settings reaches the theme
 * through the same flow that any other observer sees.
 */
@HiltViewModel
class AppearanceState @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    val themeMode: StateFlow<Int> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences.THEME_SYSTEM)
    val animationsMode: StateFlow<Int> = prefs.animationsMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences.MOTION_SYSTEM)
    val highContrast: StateFlow<Boolean> = prefs.highContrast
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setThemeMode(value: Int) { viewModelScope.launch { prefs.setThemeMode(value) } }
    fun setAnimationsMode(value: Int) { viewModelScope.launch { prefs.setAnimationsMode(value) } }
    fun setHighContrast(value: Boolean) { viewModelScope.launch { prefs.setHighContrast(value) } }
}

/**
 * [GuardiaTheme] with the user's appearance preferences applied.
 *
 * Every activity that shows Guardia UI uses this rather than [GuardiaTheme] directly. That matters
 * most for the ones that are *not* the main activity: the app-lock gate, the stop-guard PIN and the
 * per-app face check all appear over other apps, and a user who chose "always dark" noticing a
 * white overlay flash across their banking app would reasonably call that a bug.
 *
 * The one screen that deliberately does not use it is the decoy — it is supposed to look like an
 * unrelated, boring app, so it brings its own colours.
 */
@Composable
fun GuardiaAppTheme(content: @Composable () -> Unit) {
    val state: AppearanceState = hiltViewModel()
    val themeMode by state.themeMode.collectAsStateWithLifecycle()
    val animationsMode by state.animationsMode.collectAsStateWithLifecycle()
    val highContrast by state.highContrast.collectAsStateWithLifecycle()

    val dark = when (themeMode) {
        AppPreferences.THEME_DARK -> true
        AppPreferences.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    // null means "no opinion" — rememberReducedMotion falls through to the system animator scale.
    val motion: Boolean? = when (animationsMode) {
        AppPreferences.MOTION_ON -> true
        AppPreferences.MOTION_OFF -> false
        else -> null
    }
    GuardiaTheme(darkTheme = dark, highContrast = highContrast) {
        CompositionLocalProvider(LocalMotionOverride provides motion) { content() }
    }
}

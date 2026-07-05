package com.guardia.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.system.CameraMicMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Surfaces live camera/mic usage from [CameraMicMonitor] to the privacy-monitor screen. Starts the
 * monitor while the screen is open and releases it when the screen goes away.
 */
@HiltViewModel
class CameraMicViewModel @Inject constructor(
    private val monitor: CameraMicMonitor,
) : ViewModel() {

    val state: StateFlow<CameraMicMonitor.State> =
        monitor.state.stateIn(viewModelScope, SharingStarted.Eagerly, CameraMicMonitor.State())

    init { monitor.start() }

    override fun onCleared() {
        monitor.stop()
        super.onCleared()
    }
}

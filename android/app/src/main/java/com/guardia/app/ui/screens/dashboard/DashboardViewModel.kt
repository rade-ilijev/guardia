package com.guardia.app.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.guard.GuardActivity
import com.guardia.app.core.guard.GuardActivityTracker
import com.guardia.app.core.guard.GuardController
import com.guardia.app.core.guard.GuardState
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: AppPreferences,
    private val people: PeopleRepository,
    private val events: EventsRepository,
    guardActivityTracker: GuardActivityTracker,
) : ViewModel() {

    val guardState: StateFlow<GuardState> = GuardController.state

    /** Rolling 24h estimate of the battery Guardia's own checks are responsible for. */
    val appActivity: StateFlow<GuardActivity> = guardActivityTracker.activity

    val peopleCount: StateFlow<Int> = people.people
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun isIntruder(e: GuardEvent) =
        e.type == GuardEvent.Type.INTRUDER_LOCK ||
            e.type == GuardEvent.Type.UNKNOWN_FACE ||
            e.type == GuardEvent.Type.WRONG_UNLOCK

    val intruderCount: StateFlow<Int> = events.events
        .map { list -> list.count { isIntruder(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Timestamp (ms) of the most recent intruder event, or null if none. */
    val lastIntruderAt: StateFlow<Long?> = events.events
        .map { list -> list.filter { isIntruder(it) }.maxOfOrNull { it.timestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pinSet: StateFlow<Boolean> = prefs.pinIsSet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val setupDismissed: StateFlow<Boolean> = prefs.setupDismissed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val testMode: StateFlow<Boolean> = prefs.testMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 0 = Battery saver, 1 = Balanced, 2 = Max security. */
    val responsiveness: StateFlow<Int> = prefs.responsiveness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /** Whether the user has accepted the prominent background-camera disclosure. */
    val disclosureAccepted: StateFlow<Boolean> = prefs.guardDisclosureAccepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Records acceptance of the background-camera disclosure, then starts guarding. */
    fun acceptDisclosureAndStart() {
        viewModelScope.launch { prefs.setGuardDisclosureAccepted(true) }
        if (!GuardController.isProtected) toggleGuarding()
    }

    fun toggleGuarding() {
        GuardController.toggle(appContext)
        viewModelScope.launch {
            val on = GuardController.isProtected
            prefs.setGuardingEnabled(on)
            events.log(
                if (on) GuardEvent.Type.GUARDING_STARTED else GuardEvent.Type.GUARDING_STOPPED,
                if (on) "Guarding started" else "Guarding stopped",
            )
        }
    }

    fun setTestMode(value: Boolean) {
        viewModelScope.launch { prefs.setTestMode(value) }
    }

    /**
     * Immediately locks the device to prove the lock mechanism works on this hardware. Returns
     * whether a lock was actually issued (false = neither Device Admin nor the accessibility service
     * is available, so guarding can detect but not lock).
     */
    fun testDeviceLock(): Boolean =
        com.guardia.app.core.system.DeviceAdminManager.lockNow(appContext)

    /** Whether guarding currently has any way to lock the device. */
    fun canLockDevice(): Boolean =
        com.guardia.app.core.system.DeviceAdminManager.canLock(appContext)

    fun dismissSetup() {
        viewModelScope.launch { prefs.setSetupDismissed(true) }
    }
}

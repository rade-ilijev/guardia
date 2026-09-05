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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A recent unknown-face lock the owner can turn into a training sample ("was that you?"). */
data class FalseLockPrompt(val at: Long, val photoPath: String)

/** An intruder event the owner has not acknowledged yet, and how many came with it. */
data class IntruderAlert(val at: Long, val count: Int)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: AppPreferences,
    private val people: PeopleRepository,
    private val events: EventsRepository,
    private val intruders: com.guardia.app.data.IntruderRepository,
    private val quality: com.guardia.app.core.ml.FaceQualityAnalyzer,
    private val embedder: com.guardia.app.core.ml.FaceEmbedder,
    private val integrity: com.guardia.app.core.security.IntegrityGuard,
    guardActivityTracker: GuardActivityTracker,
) : ViewModel() {

    /** One-shot environment integrity check surfaced as a dashboard warning when not clean. */
    val integrityWarning: StateFlow<String?> = kotlinx.coroutines.flow.flow {
        val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { integrity.check() }.getOrNull()
        }
        emit(
            when {
                report == null -> null
                report.rooted -> "This device appears to be rooted. Rooted devices let malware bypass Android's protections — Guardia can't fully guarantee your security here."
                !report.signatureValid -> "This copy of Guardia isn't the original signed app. Reinstall it from a trusted source — a repackaged build could be compromised."
                report.debuggerAttached -> "A debugger is attached to Guardia. If you didn't do this deliberately, close it."
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val guardState: StateFlow<GuardState> = GuardController.state

    /** True while checks are paused because the phone is on a trusted Wi-Fi network. */
    val relaxedOnWifi: StateFlow<Boolean> = GuardController.relaxedOnTrustedWifi

    /** Non-null while a fresh unknown-face lock is waiting for the owner's verdict. */
    val falseLockPrompt: StateFlow<FalseLockPrompt?> = kotlinx.coroutines.flow.combine(
        prefs.pendingFalseLockAt,
        prefs.pendingFalseLockPhoto,
    ) { at, path ->
        if (at != 0L && path != null && System.currentTimeMillis() - at <= FALSE_LOCK_WINDOW_MS) {
            FalseLockPrompt(at, path)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Enabled, non-blocked people the false-lock sample could be assigned to. */
    val owners: StateFlow<List<com.guardia.app.domain.model.Person>> = people.people
        .map { list -> list.filter { !it.blocked && it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "It was me": re-embed the lock's capture and add it to [personId]'s face profile. */
    fun confirmFalseLock(personId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val path = prefs.pendingFalseLockPhoto.first()
            val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    val bytes = intruders.decrypt(path ?: return@runCatching "Couldn't read that photo")
                        ?: return@runCatching "Couldn't read that photo"
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@runCatching "Couldn't read that photo"
                    val face = quality.detect(bmp)
                        .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        ?: return@runCatching "No clear face in that photo"
                    val aligned = com.guardia.app.core.ml.FaceAligner.align(bmp, face)
                    people.addSample(personId, embedder.embed(aligned), null)
                    events.log(GuardEvent.Type.FALSE_LOCK, "Learned from a false lock")
                    "Learned — recognition will improve"
                }.getOrElse { "Couldn't learn from that photo" }
            }
            prefs.clearPendingFalseLock()
            onResult(msg)
        }
    }

    /** "It was an intruder": keep the evidence, drop the question. */
    fun dismissFalseLock() {
        viewModelScope.launch { prefs.clearPendingFalseLock() }
    }

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

    /**
     * The unacknowledged intruder banner, or null when there is nothing to say.
     *
     * Two things used to be conflated here: that something was caught, and that the owner had been
     * told. The banner was derived from the newest intruder event alone, so it stayed up until the
     * activity log was cleared — clearing the log being the only way to stop that event being the
     * newest one. Acknowledgement is now its own stored fact.
     *
     * The minute tick is what makes the age window mean anything. Without it the cutoff was read
     * once during composition and never again, so a banner that should have expired sat there
     * until some unrelated state change happened to recompose the screen.
     */
    val intruderAlert: StateFlow<IntruderAlert?> = kotlinx.coroutines.flow.combine(
        events.events,
        prefs.intruderAlertSeenAt,
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(Unit)
                kotlinx.coroutines.delay(60_000L)
            }
        },
    ) { list, seenAt, _ ->
        val unseen = list.filter { isIntruder(it) && it.timestamp > seenAt }
        val newest = unseen.maxOfOrNull { it.timestamp }
        when {
            newest == null -> null
            System.currentTimeMillis() - newest >= RECENT_INTRUDER_WINDOW_MS -> null
            else -> IntruderAlert(newest, unseen.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Marks everything up to [at] as seen. Called when the banner is dismissed and when the owner
     * opens the evidence from it — having looked at what happened is the strongest possible
     * acknowledgement, and asking them to also dismiss the banner afterwards would be nagging.
     */
    fun acknowledgeIntruders(at: Long) {
        viewModelScope.launch { prefs.setIntruderAlertSeenAt(at) }
    }

    /**
     * The three newest log entries, for the home screen's peek.
     *
     * Three because the point is a glance, not a log: enough to answer "has anything happened?"
     * without reproducing the Activity screen on the home screen and giving the user two places to
     * read the same thing.
     */
    val recentEvents: StateFlow<List<GuardEvent>> = events.events
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinSet: StateFlow<Boolean> = prefs.pinIsSet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * True when the user has a PIN but no recovery code — i.e. one forgotten PIN away from having
     * to reinstall. Recovery codes were added after launch, so everyone who onboarded before it
     * lands in this state and has no way of knowing.
     */
    val needsRecoveryCode: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(prefs.pinIsSet, prefs.recoveryCodeSet) { pin, recovery ->
            pin && !recovery
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    private companion object {
        /** How long after an unknown-face lock the "was that you?" question stays relevant. */
        const val FALSE_LOCK_WINDOW_MS = 30 * 60_000L

        /**
         * How long an unacknowledged intruder event stays pinned to the home screen. A day, because
         * the phone may well have been left somewhere overnight — but a day and no longer, because
         * a banner that never goes away stops being read.
         */
        const val RECENT_INTRUDER_WINDOW_MS = 24L * 60 * 60 * 1000
    }
}

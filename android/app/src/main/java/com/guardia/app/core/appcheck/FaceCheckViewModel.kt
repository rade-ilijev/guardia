package com.guardia.app.core.appcheck

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.ml.BitmapUtils
import com.guardia.app.core.ml.FacePipeline
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

enum class CheckPhase { CHECKING, PASSED, FAILED }

/** How the check screen looks while verifying: an active spinner, a frosted blur, or a static freeze. */
enum class CheckStyle { LOADING, BLUR, FREEZE }

data class FaceCheckState(
    val phase: CheckPhase = CheckPhase.CHECKING,
    val message: String = "Verifying it's you…",
    /** When true (failure, not test mode), the host should close the app by going home. */
    val closeApp: Boolean = false,
    /** When true (failure, not test mode), the host should also lock the device via Device Admin. */
    val lockDevice: Boolean = false,
    /** When true the host brightens the screen to white to light the face in the dark. */
    val flash: Boolean = false,
    val style: CheckStyle = CheckStyle.LOADING,
)

/**
 * Runs a one-shot face check for a guarded app. Streams camera frames through [FacePipeline] until
 * the owner is recognized or a short deadline elapses, then reports pass/fail. On failure outside of
 * test mode it signals the host to close the app.
 */
@HiltViewModel
class FaceCheckViewModel @Inject constructor(
    private val facePipeline: FacePipeline,
    private val prefs: AppPreferences,
    private val events: EventsRepository,
    private val intruders: IntruderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FaceCheckState())
    val state: StateFlow<FaceCheckState> = _state.asStateFlow()

    private val processing = AtomicBoolean(false)
    private val settled = AtomicBoolean(false)
    private var deadlineJob: Job? = null

    @Volatile private var sensitivity = 0.5f
    @Volatile private var captureIntruders = true
    @Volatile private var testMode = false
    @Volatile private var lockOnFail = true
    /** True once we've actually seen a face that isn't the owner (vs. just failing to get a look). */
    @Volatile private var sawUnauthorizedFace = false
    /** Consecutive frames showing an unauthorized/extra face — used to fail fast instead of waiting. */
    @Volatile private var unauthorizedHits = 0
    @Volatile private var startedAt = 0L
    @Volatile private var lastFrame: Bitmap? = null
    @Volatile private var lastRotation = 0
    @Volatile private var flashOn = false
    @Volatile private var style = CheckStyle.LOADING
    private var appLabel = "this app"
    private var pkg = ""

    fun start(pkg: String, appLabel: String) {
        if (deadlineJob != null) return
        this.pkg = pkg
        this.appLabel = appLabel
        startedAt = System.currentTimeMillis()
        viewModelScope.launch {
            sensitivity = prefs.sensitivity.first()
            captureIntruders = prefs.captureIntruders.first()
            testMode = prefs.testMode.first()
            lockOnFail = prefs.appLockOnFail.first()
            style = when (prefs.appCheckStyle.first()) {
                1 -> CheckStyle.BLUR
                2 -> CheckStyle.FREEZE
                else -> CheckStyle.LOADING
            }
            _state.value = _state.value.copy(style = style)
        }
        deadlineJob = viewModelScope.launch {
            delay(DEADLINE_MS)
            // On timeout, only treat it as an intruder (and lock) if we actually saw someone who
            // isn't the owner. A pure "couldn't get a look" just closes the app.
            fail("Couldn't verify you", intruderSeen = sawUnauthorizedFace)
        }
    }

    fun onFrame(bitmap: Bitmap, rotation: Int) {
        if (_state.value.phase != CheckPhase.CHECKING) return
        // Always keep the most recent frame so a failure can save an intruder selfie.
        lastFrame = bitmap
        lastRotation = rotation
        // Dark scene? Brighten the screen to white to light the face (front "flash").
        // Only when showing the loading screen — blur/freeze paint the app, so we don't whiten it.
        if (style == CheckStyle.LOADING && !flashOn && BitmapUtils.averageLuminance(bitmap) < FLASH_TRIGGER_LUMA) {
            flashOn = true
            _state.value = _state.value.copy(flash = true)
        }
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val analysis = facePipeline.analyze(bitmap, rotation, sensitivity)
                when (analysis.outcome) {
                    FacePipeline.Outcome.MATCH -> pass()
                    FacePipeline.Outcome.BLOCKED -> fail("Blocked person detected", intruderSeen = true)
                    // A face is visible but it isn't the owner. Remember it (so a timeout still locks)
                    // and, after a short grace for the owner to settle, fail fast rather than leaving
                    // the app on screen for the whole deadline.
                    FacePipeline.Outcome.NO_MATCH, FacePipeline.Outcome.MULTIPLE_FACES -> {
                        sawUnauthorizedFace = true
                        unauthorizedHits++
                        if (System.currentTimeMillis() - startedAt > FAST_FAIL_GRACE_MS &&
                            unauthorizedHits >= FAST_FAIL_HITS
                        ) {
                            fail("Not recognized", intruderSeen = true)
                        }
                    }
                    else -> Unit // NO_FACE / INCONCLUSIVE: keep trying until the deadline
                }
            } finally {
                processing.set(false)
            }
        }
    }

    private fun pass() {
        if (!settled.compareAndSet(false, true)) return
        deadlineJob?.cancel()
        _state.value = _state.value.copy(phase = CheckPhase.PASSED, message = "Verified", closeApp = false)
    }

    /**
     * @param intruderSeen true when an actual unauthorized person was detected (blocked person, or
     *   an unrecognized/extra face). Locking the whole device only happens in that case (and only
     *   when [lockOnFail] is on and we're not in test mode); otherwise we just close the app.
     */
    private fun fail(reason: String, intruderSeen: Boolean) {
        if (!settled.compareAndSet(false, true)) return
        deadlineJob?.cancel()
        val willLock = !testMode && lockOnFail && intruderSeen
        val frame = lastFrame
        val rotation = lastRotation
        viewModelScope.launch(Dispatchers.Default) {
            // Only save an intruder selfie when we actually saw an unauthorized face.
            var photoPath: String? = null
            if (captureIntruders && intruderSeen && frame != null) {
                val upright = runCatching { BitmapUtils.rotate(frame, rotation) }.getOrNull() ?: frame
                val jpeg = runCatching { BitmapUtils.toJpeg(upright) }.getOrNull()
                if (jpeg != null) {
                    photoPath = runCatching { intruders.saveCapture(jpeg, appLabel) }.getOrNull()
                }
            }
            val type = if (intruderSeen) GuardEvent.Type.INTRUDER_LOCK else GuardEvent.Type.UNKNOWN_FACE
            val outcome = when {
                testMode -> "test mode (no action)"
                willLock -> "$appLabel closed & device locked"
                else -> "$appLabel closed"
            }
            runCatching { events.log(type, "$reason in $appLabel - $outcome", photoPath) }
        }
        // Outside test mode we always close the guarded app; we additionally lock the device when an
        // unauthorized person was actually present. Test mode just reveals the app with a note.
        _state.value = _state.value.copy(
            phase = CheckPhase.FAILED,
            message = when {
                testMode -> "Test mode - would close" + if (willLockHint(intruderSeen)) " & lock" else ""
                willLock -> "Not recognized - locking"
                else -> "Closing $appLabel"
            },
            closeApp = !testMode,
            lockDevice = willLock,
        )
    }

    /** Whether, outside test mode, this failure would have locked the device (for the test-mode note). */
    private fun willLockHint(intruderSeen: Boolean) = lockOnFail && intruderSeen

    override fun onCleared() {
        lastFrame = null
        super.onCleared()
    }

    companion object {
        private const val DEADLINE_MS = 6000L
        /** Mean frame luminance (0..255) below which we turn on the screen flash. */
        private const val FLASH_TRIGGER_LUMA = 80f
        /** Grace after start before fast-fail can trigger, giving the owner time to be recognized. */
        private const val FAST_FAIL_GRACE_MS = 800L
        /** Consecutive unauthorized-face frames (after the grace) that trigger an immediate fail. */
        private const val FAST_FAIL_HITS = 4
    }
}

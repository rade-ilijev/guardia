package com.guardia.app.core.guard

import android.content.Context
import android.util.Log
import com.guardia.app.core.alerts.AlertsManager
import com.guardia.app.core.ml.FacePipeline
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.core.system.TestNotifier
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Executes the response to an intruder: capture, log, and (unless testing) lock the device. */
@Singleton
class Responder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intruders: IntruderRepository,
    private val events: EventsRepository,
    private val alerts: AlertsManager,
) {
    /**
     * Evidence/alert I/O runs here — an application-scoped IO scope that is deliberately NOT tied to
     * the GuardService/frame-analysis lifecycle. Locking the device turns the screen off, after which
     * some OEMs immediately freeze the calling context; if the capture ran inline it could be starved
     * before it finished (the bug where real-mode locked but never saved the selfie). Running it here,
     * off the main thread and independent of the caller, lets it complete regardless.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun onIntruder(
        jpegBytes: ByteArray?,
        analysis: FacePipeline.Analysis,
        captureEnabled: Boolean,
        testMode: Boolean,
    ) {
        val multiple = analysis.outcome == FacePipeline.Outcome.MULTIPLE_FACES
        val noFace = analysis.outcome == FacePipeline.Outcome.NO_FACE
        val blocked = analysis.outcome == FacePipeline.Outcome.BLOCKED
        val lowLight = analysis.outcome == FacePipeline.Outcome.INCONCLUSIVE
        val type = if (multiple || blocked) GuardEvent.Type.INTRUDER_LOCK else GuardEvent.Type.UNKNOWN_FACE
        val what = when {
            blocked -> "Blocked person detected${analysis.personName?.let { ": $it" } ?: ""}"
            multiple -> "Multiple faces detected"
            noFace -> "No face visible"
            lowLight -> "Too dark to verify"
            else -> "Unrecognized face detected"
        }

        if (testMode) {
            // Test mode never locks — it saves the selfie (if enabled) and posts a preview so the
            // whole pipeline can be verified safely.
            ioScope.launch {
                val photoPath = if (captureEnabled && jpegBytes != null) {
                    runCatching { intruders.saveCapture(jpegBytes, "Guarding") }.getOrNull()
                } else null
                runCatching { events.log(type, "$what - test mode (no lock)", photoPath) }
                runCatching {
                    TestNotifier.showFaceResult(context, "Would lock now", "$what (test mode, device not locked)")
                }
            }
            return
        }

        // REAL MODE.
        // 1) Start persisting the evidence + alert on the independent IO scope FIRST, so encryption
        //    and the DB write get a head start on a background thread and can finish even after the
        //    lock turns the screen off. This is decoupled from the "Capture intruders" setting, which
        //    only controls whether the selfie itself is saved.
        ioScope.launch {
            val photoPath = if (captureEnabled && jpegBytes != null) {
                runCatching { intruders.saveCapture(jpegBytes, "Guarding") }
                    .onFailure { Log.w(TAG, "intruder capture failed", it) }
                    .getOrNull()
            } else null
            runCatching { events.log(type, "$what - locked", photoPath) }
            runCatching { alerts.onSecurityEvent("$what and device locked.", jpegBytes) }
        }

        // 2) Lock the device IMMEDIATELY and synchronously — the critical security action, with no
        //    dependence on any of the I/O above. Tries Device Admin, then an accessibility fallback.
        val locked = DeviceAdminManager.lockNow(context)
        if (!locked) Log.w(TAG, "lockNow could not lock (no Device Admin and no accessibility service)")
    }

    private companion object {
        const val TAG = "Responder"
    }
}

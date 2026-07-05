package com.guardia.app.core.guard

import android.content.Context
import com.guardia.app.core.alerts.AlertsManager
import com.guardia.app.core.ml.FacePipeline
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.core.system.TestNotifier
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.qualifiers.ApplicationContext
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

        // The lock is the critical security action and must never depend on (or be blocked by)
        // capturing a selfie, encrypting it, writing to the database, or sending an alert. Outside
        // test mode we therefore lock FIRST, then do the best-effort evidence/logging/alert work —
        // each guarded so a failure in one can never prevent or undo the lock. This is independent
        // of the "Capture intruders" setting, which only controls whether a selfie is saved.
        if (!testMode) {
            runCatching { DeviceAdminManager.lockNow(context) }
        }

        var photoPath: String? = null
        if (captureEnabled && jpegBytes != null) {
            photoPath = runCatching { intruders.saveCapture(jpegBytes, "Guarding") }.getOrNull()
        }

        if (testMode) {
            runCatching { events.log(type, "$what - test mode (no lock)", photoPath) }
            runCatching {
                TestNotifier.showFaceResult(context, "Would lock now", "$what (test mode, device not locked)")
            }
        } else {
            runCatching { events.log(type, "$what - locked", photoPath) }
            runCatching { alerts.onSecurityEvent("$what and device locked.", jpegBytes) }
        }
    }
}

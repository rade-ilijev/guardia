package com.guardia.app.core.guard

import com.guardia.app.core.ml.FacePipeline
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a stream of face-analysis results into a guarding decision, with a grace
 * window so a single bad frame does not lock the device.
 */
@Singleton
class RulesEngine @Inject constructor() {

    enum class Decision { OK, IGNORE, LOCK }

    /** Which outcomes are allowed to lock the device. All default on except no-face is user-driven. */
    data class Triggers(
        val unknownFace: Boolean = true,
        val blockedPerson: Boolean = true,
        val multipleFaces: Boolean = true,
        val noFace: Boolean = false,
    )

    private var consecutiveMisses = 0
    private var noFaceMisses = 0
    private val missesBeforeLock = 2
    private val noFaceBeforeLock = 3

    fun reset() {
        consecutiveMisses = 0
        noFaceMisses = 0
    }

    /**
     * Turns a single analysis into a decision, honoring which [triggers] the user has enabled. A
     * short grace window (consecutive bad frames) prevents a single bad frame from locking.
     */
    fun onAnalysis(analysis: FacePipeline.Analysis, triggers: Triggers = Triggers()): Decision =
        when (analysis.outcome) {
            FacePipeline.Outcome.MATCH -> {
                consecutiveMisses = 0
                noFaceMisses = 0
                Decision.OK
            }
            FacePipeline.Outcome.NO_MATCH -> intruderFrame(triggers.unknownFace)
            FacePipeline.Outcome.BLOCKED -> intruderFrame(triggers.blockedPerson)
            FacePipeline.Outcome.MULTIPLE_FACES -> intruderFrame(triggers.multipleFaces)
            FacePipeline.Outcome.NO_FACE -> {
                if (triggers.noFace) {
                    noFaceMisses++
                    if (noFaceMisses >= noFaceBeforeLock) {
                        noFaceMisses = 0
                        Decision.LOCK
                    } else Decision.IGNORE
                } else Decision.IGNORE
            }
            FacePipeline.Outcome.INCONCLUSIVE -> Decision.IGNORE
        }

    /** A frame showing someone who isn't the owner; locks after the grace window if [enabled]. */
    private fun intruderFrame(enabled: Boolean): Decision {
        if (!enabled) return Decision.IGNORE
        consecutiveMisses++
        return if (consecutiveMisses >= missesBeforeLock) {
            consecutiveMisses = 0
            Decision.LOCK
        } else Decision.IGNORE
    }
}

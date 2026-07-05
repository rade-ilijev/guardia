package com.guardia.app

import com.guardia.app.core.guard.RulesEngine
import com.guardia.app.core.ml.FacePipeline
import org.junit.Assert.assertEquals
import org.junit.Test

class RulesEngineTest {

    private fun analysis(outcome: FacePipeline.Outcome) =
        FacePipeline.Analysis(outcome = outcome, similarity = 0f, personName = null, personId = null)

    @Test
    fun match_returnsOk() {
        val engine = RulesEngine()
        assertEquals(RulesEngine.Decision.OK, engine.onAnalysis(analysis(FacePipeline.Outcome.MATCH)))
    }

    @Test
    fun singleMiss_isIgnoredByGraceWindow() {
        val engine = RulesEngine()
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH)))
    }

    @Test
    fun twoConsecutiveMisses_lock() {
        val engine = RulesEngine()
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH))
        assertEquals(RulesEngine.Decision.LOCK, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH)))
    }

    @Test
    fun matchBetweenMisses_resetsGraceWindow() {
        val engine = RulesEngine()
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH))
        engine.onAnalysis(analysis(FacePipeline.Outcome.MATCH))
        // After a reset, a single miss should be ignored again rather than locking.
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH)))
    }

    @Test
    fun noFace_isIgnoredAndDoesNotCountAsMiss() {
        val engine = RulesEngine()
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE))
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE))
        // No-face frames never accumulate toward a lock.
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE)))
    }

    @Test
    fun noFace_locksAfterStreakWhenEnabled() {
        val engine = RulesEngine()
        val t = RulesEngine.Triggers(noFace = true)
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE), t))
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE), t))
        assertEquals(RulesEngine.Decision.LOCK, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE), t))
    }

    @Test
    fun noFace_matchResetsNoFaceStreak() {
        val engine = RulesEngine()
        val t = RulesEngine.Triggers(noFace = true)
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE), t)
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE), t)
        engine.onAnalysis(analysis(FacePipeline.Outcome.MATCH), t)
        // Streak cleared, so the next no-face frame is ignored again.
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_FACE), t))
    }

    @Test
    fun unknownFace_ignoredWhenTriggerDisabled() {
        val engine = RulesEngine()
        val t = RulesEngine.Triggers(unknownFace = false)
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH), t)
        // Even after repeated unknown frames, locking is suppressed when the trigger is off.
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH), t))
    }

    @Test
    fun blockedPerson_locksAfterGraceWindow() {
        val engine = RulesEngine()
        engine.onAnalysis(analysis(FacePipeline.Outcome.BLOCKED))
        assertEquals(RulesEngine.Decision.LOCK, engine.onAnalysis(analysis(FacePipeline.Outcome.BLOCKED)))
    }

    @Test
    fun reset_clearsMissCount() {
        val engine = RulesEngine()
        engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH))
        engine.reset()
        assertEquals(RulesEngine.Decision.IGNORE, engine.onAnalysis(analysis(FacePipeline.Outcome.NO_MATCH)))
    }
}

package com.guardia.app

import com.guardia.app.core.guard.CaptureGate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the guard loop's cadence. `CaptureGate` itself needs sensors, a
 * `PowerManager` and a `KeyguardManager`, so only the two pure decisions are exercised here — but
 * they are the ones with a real off-by-one to get wrong.
 */
class CaptureScheduleTest {

    @Test
    fun gapsBecomeCumulativeOffsets() {
        // The settings screen collects "then 5s later, then 10s later, then 30s later"; the
        // scheduler needs "at 5s, at 15s, at 45s". Treating the gaps as offsets would fire the
        // whole ramp inside the first 30 seconds.
        assertArrayEquals(
            longArrayOf(5_000L, 15_000L, 45_000L),
            CaptureGate.rampOffsets(listOf(5, 10, 30)),
        )
    }

    @Test
    fun anEmptyRampProducesNoOffsets() {
        assertEquals(0, CaptureGate.rampOffsets(emptyList()).size)
    }

    @Test
    fun nonPositiveGapsAreDroppedNotCollapsed() {
        // A 0 or negative gap would otherwise schedule two checks on the same tick.
        assertArrayEquals(
            longArrayOf(5_000L, 15_000L),
            CaptureGate.rampOffsets(listOf(5, 0, 10, -3)),
        )
    }

    @Test
    fun offsetsAreStrictlyIncreasing() {
        val offsets = CaptureGate.rampOffsets(listOf(1, 2, 3, 4, 5))
        for (i in 1 until offsets.size) {
            assertTrue("offset $i did not advance", offsets[i] > offsets[i - 1])
        }
    }

    @Test
    fun higherResponsivenessMeansAShorterGapBetweenChecks() {
        val saver = CaptureGate.cadenceForLevel(0)
        val balanced = CaptureGate.cadenceForLevel(1)
        val max = CaptureGate.cadenceForLevel(2)
        assertTrue("max should check most often", max < balanced)
        assertTrue("saver should check least often", balanced < saver)
    }

    @Test
    fun anUnknownResponsivenessLevelFallsBackToBalanced() {
        // Preferences can carry a level written by an older or newer build; the safe landing is
        // the middle profile, never "never check".
        val balanced = CaptureGate.cadenceForLevel(1)
        assertEquals(balanced, CaptureGate.cadenceForLevel(-1))
        assertEquals(balanced, CaptureGate.cadenceForLevel(7))
    }
}

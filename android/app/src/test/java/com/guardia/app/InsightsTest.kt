package com.guardia.app

import com.guardia.app.ui.screens.stats.DayBar
import com.guardia.app.ui.screens.stats.StatsUi
import com.guardia.app.ui.screens.stats.StatsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Insights makes claims about the user's phone, so the two judgement calls behind those claims are
 * tested: when a peak is a real finding rather than noise, and how the false-lock rate behaves at
 * the edges of a log that rolls.
 */
class InsightsTest {

    private fun windows(vararg counts: Int): List<DayBar> =
        counts.mapIndexed { i, v -> DayBar("%02d".format(i * 3), v) }

    @Test
    fun aClearPeakIsNamedWithItsThreeHourWindow() {
        // Index 5 -> 15:00, window length 3.
        val label = StatsViewModel.busiestLabel(windows(0, 1, 0, 1, 0, 9, 1, 0))
        assertEquals("15:00 - 18:00", label)
    }

    @Test
    fun theLastWindowWrapsToMidnight() {
        assertEquals("21:00 - 00:00", StatsViewModel.busiestLabel(windows(0, 0, 0, 0, 0, 0, 0, 4)))
    }

    @Test
    fun aMarginalPeakIsNotAFinding() {
        // Leading by one is what a handful of scattered events looks like; calling it a pattern
        // would be inventing one.
        assertNull(StatsViewModel.busiestLabel(windows(3, 2, 2, 0, 0, 0, 0, 0)))
    }

    @Test
    fun aTiedPeakIsNotAFinding() {
        assertNull(StatsViewModel.busiestLabel(windows(4, 4, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun anEmptyDistributionNamesNothing() {
        assertNull(StatsViewModel.busiestLabel(windows(0, 0, 0, 0, 0, 0, 0, 0)))
        assertNull(StatsViewModel.busiestLabel(emptyList()))
    }

    @Test
    fun falseLockRateIsZeroWithNothingToDivideBy() {
        assertEquals(0f, StatsUi().falseLockRate, 0.0001f)
        assertEquals(0f, StatsUi(falseLocks = 3, unknownFaceLocks = 0).falseLockRate, 0.0001f)
    }

    @Test
    fun falseLockRateReadsAsAShareOfUnknownFaceLocks() {
        assertEquals(0.25f, StatsUi(falseLocks = 3, unknownFaceLocks = 12).falseLockRate, 0.0001f)
    }

    @Test
    fun falseLockRateCannotExceedOne() {
        // The activity log is capped, so an old UNKNOWN_FACE can age out while the FALSE_LOCK that
        // answered it is still in the window. "140% of locks were you" would be nonsense on screen.
        assertEquals(1f, StatsUi(falseLocks = 7, unknownFaceLocks = 5).falseLockRate, 0.0001f)
    }

    @Test
    fun noIntruderHistoryIsReportedAsSuch() {
        assertFalse(StatsUi().hasIntruderHistory)
        assertTrue(StatsUi(intruderTotal = 1).hasIntruderHistory)
    }
}

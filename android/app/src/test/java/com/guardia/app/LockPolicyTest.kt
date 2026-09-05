package com.guardia.app

import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules that decide when Guardia's own front door is shut: the brute-force backoff after
 * wrong PINs, and the re-lock after the app leaves the screen.
 *
 * Both were arithmetic buried inside a DataStore write and an Android clock read; they are pulled
 * out so the rules themselves can be checked, because both have failure modes that are invisible
 * in ordinary use (a backoff that wraps around to *shorter*, a grace period that never fires).
 */
class LockPolicyTest {

    // -- Wrong-PIN backoff ---------------------------------------------------------------------

    @Test
    fun noLockoutBeforeTheThreshold() {
        for (attempts in 0 until AppPreferences.LOCK_AFTER_ATTEMPTS) {
            assertEquals("attempt $attempts", 0L, AppPreferences.lockoutDurationMs(attempts))
        }
    }

    @Test
    fun theFirstLockoutIsThirtySeconds() {
        assertEquals(30_000L, AppPreferences.lockoutDurationMs(AppPreferences.LOCK_AFTER_ATTEMPTS))
    }

    @Test
    fun eachFurtherFailureDoublesTheWait() {
        val base = AppPreferences.LOCK_AFTER_ATTEMPTS
        assertEquals(60_000L, AppPreferences.lockoutDurationMs(base + 1))
        assertEquals(120_000L, AppPreferences.lockoutDurationMs(base + 2))
        assertEquals(240_000L, AppPreferences.lockoutDurationMs(base + 3))
    }

    @Test
    fun theWaitIsCappedAtFifteenMinutes() {
        val cap = 15 * 60_000L
        assertEquals(cap, AppPreferences.lockoutDurationMs(AppPreferences.LOCK_AFTER_ATTEMPTS + 10))
        assertEquals(cap, AppPreferences.lockoutDurationMs(AppPreferences.LOCK_AFTER_ATTEMPTS + 1000))
    }

    @Test
    fun theWaitNeverShrinksAsAttemptsPileUp() {
        // The reason the shift is clamped: `shl` counts modulo 64 on a Long, so an unclamped
        // 64th doubling would wrap to the base duration and quietly *reward* a persistent guesser.
        var previous = 0L
        for (attempts in 0..200) {
            val current = AppPreferences.lockoutDurationMs(attempts)
            assertTrue("lockout shrank at attempt $attempts", current >= previous)
            previous = current
        }
    }

    // -- Re-lock on leaving the app ------------------------------------------------------------

    @Test
    fun theDefaultGraceRelocksImmediately() {
        // Grace 0 is the default, and it has to mean "always": a same-millisecond return is still
        // someone having left the app.
        assertTrue(AppViewModel.shouldRelock(awayMs = 0L, graceSeconds = 0))
        assertTrue(AppViewModel.shouldRelock(awayMs = 1L, graceSeconds = 0))
    }

    @Test
    fun aBriefTripInsideTheGracePeriodDoesNotRelock() {
        // Granting a permission bounces through Android Settings; with a 2-minute grace that trip
        // must not cost the user their PIN.
        assertFalse(AppViewModel.shouldRelock(awayMs = 20_000L, graceSeconds = 120))
        assertFalse(AppViewModel.shouldRelock(awayMs = 119_999L, graceSeconds = 120))
    }

    @Test
    fun theGraceBoundaryItselfRelocks() {
        assertTrue(AppViewModel.shouldRelock(awayMs = 120_000L, graceSeconds = 120))
        assertTrue(AppViewModel.shouldRelock(awayMs = 120_001L, graceSeconds = 120))
    }

    @Test
    fun anOvernightAbsenceAlwaysRelocks() {
        val hours = 12 * 60 * 60 * 1000L
        for (grace in intArrayOf(0, 30, 120, 300)) {
            assertTrue("grace $grace", AppViewModel.shouldRelock(hours, grace))
        }
    }
}

package com.guardia.app.core.guard

/**
 * High-level guarding state shown on the dashboard and reflected in the
 * foreground-service notification and Quick Settings tile.
 */
enum class GuardState {
    /** Guarding is off (user stopped it). */
    STOPPED,

    /** Guarding is active and watching who uses the device. */
    PROTECTED,

    /** Temporarily paused (e.g. via voice safeword or deliberate lend). */
    PAUSED,

    /** Running but a prerequisite is missing (permission revoked, etc.). */
    NEEDS_ATTENTION,
}

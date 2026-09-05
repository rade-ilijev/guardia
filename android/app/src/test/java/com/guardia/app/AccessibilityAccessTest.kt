package com.guardia.app

import com.guardia.app.core.system.AccessibilityAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of `Settings.Secure.enabled_accessibility_services`.
 *
 * This is the check that decides whether Guardia believes App Lock is running, so getting it wrong
 * is not a cosmetic bug: a false positive means the app reassures the user it is guarding when the
 * grant is gone. The two flavours share a class name and differ only by an application-ID suffix,
 * which is exactly the shape that a naive `contains` gets wrong in both directions.
 */
class AccessibilityAccessTest {

    private val full = "com.guardia.app.full"
    private val play = "com.guardia.app"
    private val service = "com.guardia.app.core.system.GuardAccessibilityService"

    @Test
    fun `finds the service in a fully qualified entry`() {
        assertTrue(AccessibilityAccess.listsService("$full/$service", full))
    }

    @Test
    fun `finds the service in the short form the platform sometimes writes`() {
        assertTrue(AccessibilityAccess.listsService("$full/.core.system.GuardAccessibilityService", full))
    }

    @Test
    fun `finds the service alongside other enabled services`() {
        val setting = "com.google.android.marvin.talkback/.TalkBackService:" +
            "$full/$service:" +
            "com.example.other/.Service"
        assertTrue(AccessibilityAccess.listsService(setting, full))
    }

    /**
     * The sibling-build case. `com.guardia.app` is a string prefix of `com.guardia.app.full`, so a
     * substring match would have the Play build report itself as enabled whenever the sideload
     * build's service was on — two different apps, one of them not running.
     */
    @Test
    fun `does not mistake the other flavour for itself`() {
        assertFalse(AccessibilityAccess.listsService("$full/$service", play))
        assertFalse(AccessibilityAccess.listsService("$play/$service", full))
    }

    @Test
    fun `does not match a different service from the same package`() {
        assertFalse(AccessibilityAccess.listsService("$full/com.guardia.app.core.system.SomethingElse", full))
    }

    @Test
    fun `treats absent or empty settings as not enabled`() {
        assertFalse(AccessibilityAccess.listsService(null, full))
        assertFalse(AccessibilityAccess.listsService("", full))
        assertFalse(AccessibilityAccess.listsService("   ", full))
    }

    @Test
    fun `ignores malformed entries instead of throwing`() {
        assertFalse(AccessibilityAccess.listsService("garbage", full))
        assertFalse(AccessibilityAccess.listsService("/$service", full))
        assertTrue(AccessibilityAccess.listsService("garbage:$full/$service", full))
    }

    /** Guards the literal in AccessibilityAccess against a rename of the service class. */
    @Test
    fun `the matched class name is still the services own`() {
        assertTrue(
            AccessibilityAccess.listsService(
                "$full/com.guardia.app.core.system.GuardAccessibilityService",
                full,
            ),
        )
    }
}

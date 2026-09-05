package com.guardia.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Pins the capabilities each flavour's accessibility service declares.
 *
 * Android derives an `AccessibilityServiceInfo`'s capabilities from these attributes, and the user
 * consents to *that set*. Change one and the platform drops the service from the enabled list on
 * update, because the thing they agreed to is no longer the thing being asked for. On a sideloaded
 * build that is a nuisance a developer notices immediately. On the Play build it is every existing
 * user silently losing App Lock after a routine update, discovering it only when a locked app opens
 * without asking — which is exactly the failure this app exists to prevent.
 *
 * So these values are not "config", they are a compatibility contract with every user who already
 * said yes. If a change here is deliberate, update the expectations below and treat it as a release
 * that costs every user their grant: it needs a note in the release, and the in-app warning
 * ([com.guardia.app.core.system.AccessibilityAccess]) has to carry them back.
 *
 * The Play config deliberately declares *less* than the sideload one — no screenshot capability and
 * no window-content retrieval — because those are the capabilities Play review scrutinises most and
 * the store build does not need them.
 */
class AccessibilityConfigTest {

    /**
     * Gradle runs unit tests with the module directory as the working directory, but that is a
     * default rather than a guarantee, so the module prefix is tried too — a test that silently
     * stopped finding the file would pass while checking nothing.
     */
    private fun resolve(path: String): File =
        listOf(File(path), File("app/$path"), File("android/app/$path")).firstOrNull { it.isFile }
            ?: File(path)

    private fun config(path: String): Element {
        val file = resolve(path)
        assertTrue(
            "Missing $path (looked from ${File(".").absolutePath}) — if the accessibility config " +
                "moved, this test must move with it.",
            file.isFile,
        )
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        return doc.documentElement
    }

    private fun Element.androidAttr(name: String): String? =
        getAttributeNS(ANDROID_NS, name).takeIf { it.isNotEmpty() }

    @Test
    fun `sideload build declares exactly the capabilities it is documented to need`() {
        val cfg = config("src/main/res/xml/accessibility_service_config.xml")
        assertEquals("true", cfg.androidAttr("canRetrieveWindowContent"))
        assertEquals("true", cfg.androidAttr("canTakeScreenshot"))
        assertTrue(
            "flagRetrieveInteractiveWindows is part of what the user consented to",
            cfg.androidAttr("accessibilityFlags").orEmpty().contains("flagRetrieveInteractiveWindows"),
        )
    }

    @Test
    fun `play build stays on the minimal capability set`() {
        val cfg = config("src/play/res/xml/accessibility_service_config.xml")
        assertEquals("false", cfg.androidAttr("canRetrieveWindowContent"))
        assertEquals(
            "The Play build must not ask for screen capture; adding it revokes every user's grant.",
            null,
            cfg.androidAttr("canTakeScreenshot"),
        )
        assertEquals("flagDefault", cfg.androidAttr("accessibilityFlags"))
    }

    /**
     * Only an app that genuinely assists users with disabilities may claim this, and claiming it
     * exempts the app from the prominent disclosure Play's AccessibilityService policy requires.
     * Guardia is a security app, so both configs say false and
     * [com.guardia.app.ui.components.rememberAccessibilityOptIn] provides the disclosure.
     */
    @Test
    fun `neither build claims to be an accessibility tool`() {
        listOf(
            "src/main/res/xml/accessibility_service_config.xml",
            "src/play/res/xml/accessibility_service_config.xml",
        ).forEach { path ->
            assertEquals(path, "false", config(path).androidAttr("isAccessibilityTool"))
        }
    }

    /** The component name is half of the consent; renaming the class revokes it just as surely. */
    @Test
    fun `the service is still declared under the name users granted`() {
        val manifest = resolve("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "GuardAccessibilityService must keep its name — the enabled-services list stores it.",
            manifest.contains("android:name=\".core.system.GuardAccessibilityService\""),
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

package com.guardia.app.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local environment-integrity checks for Guardia and the device it runs on. Everything here is
 * heuristic and on-device — there is no server attestation (that would break the "nothing leaves
 * the device" promise), so treat results as signals, not proof. Still, on a security app the owner
 * deserves to know when the ground under it is compromised (root, debugger, repackaging).
 */
@Singleton
class IntegrityGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Report(
        val rooted: Boolean,
        val debuggerAttached: Boolean,
        val likelyEmulator: Boolean,
        val signatureValid: Boolean,
    ) {
        /** True when nothing suspicious was found. */
        val clean: Boolean get() = !rooted && !debuggerAttached && signatureValid
    }

    fun check(): Report = Report(
        rooted = isRooted(),
        debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
        likelyEmulator = isEmulator(),
        signatureValid = signatureValid(),
    )

    // --- Root ---

    /** Common signals of a rooted device: su binaries, test-keys builds, and superuser packages. */
    private fun isRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        val suPaths = listOf(
            "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su", "/system/app/Superuser.apk",
        )
        if (suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }) return true
        val managers = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "com.noshufou.android.su", "com.thirdparty.superuser",
        )
        return managers.any { isPackageInstalled(it) }
    }

    private fun isPackageInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)

    // --- Emulator (informational only) ---

    private fun isEmulator(): Boolean {
        // Folded in ROOT, not the device locale. These are ASCII build strings compared against
        // ASCII literals, and on a Turkish phone `"GOLDFISH".lowercase()` is `goldfısh` with a
        // dotless i — the match would silently stop working for every user in that locale.
        val f = Build.FINGERPRINT.orEmpty().lowercase(Locale.ROOT)
        val model = Build.MODEL.orEmpty().lowercase(Locale.ROOT)
        val product = Build.PRODUCT.orEmpty().lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.ROOT)
        return f.startsWith("generic") || f.contains("emulator") || f.contains("sdk_gphone") ||
            model.contains("emulator") || model.contains("android sdk") ||
            product.contains("sdk") || hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }

    // --- Signature / repackaging ---

    /**
     * Confirms the running app is signed by the expected certificate. On a debug build the cert is
     * the local debug key, so this passes trivially; on release, a repackaged/re-signed copy (a
     * classic malware trick) has a different cert and fails. The expected hash is injected at build
     * time via BuildConfig so there is no plaintext cert to patch out.
     */
    private fun signatureValid(): Boolean {
        val expected = com.guardia.app.BuildConfig.EXPECTED_SIGNING_SHA256
        if (expected.isBlank()) return true // not pinned (debug/unconfigured) — don't false-alarm
        val actual = currentSignatureSha256() ?: return false
        return actual.equals(expected, ignoreCase = true)
    }

    private fun currentSignatureSha256(): String? = runCatching {
        val pm = context.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: return null
        val cert = sigs.firstOrNull()?.toByteArray() ?: return null
        // ROOT: this hex string is compared against a pinned signature, and a locale with its own
        // digit shapes (Arabic-Indic, say) would format it into something that never matches.
        MessageDigest.getInstance("SHA-256").digest(cert)
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
    }.getOrNull()
}

package com.guardia.app.core.backup

/**
 * The rule that decides whether a decrypted backup came from *this* install.
 *
 * It lives on its own, away from ciphers and JSON, because it is the security-critical half of
 * using a backup file to reset a forgotten PIN — and because in that form it can be tested on the
 * JVM, which nothing that touches `android.util.Base64` or `org.json` can be.
 *
 * The trap it exists to avoid: decrypting a backup proves only that you know *a* password, and
 * anyone can export a backup from their own install under a password of their choosing. Accepting
 * "it decrypted" as proof of ownership would therefore let a thief reset the PIN of a phone they
 * had just picked up. What is checked instead is provenance.
 */
internal object BackupOwnership {

    /**
     * @param fileToken the `owner` token carried by the backup, or null for a pre-v3 file.
     * @param installToken this install's token, or null if it has never exported a backup.
     * @param backupPersonIds person IDs found in the backup.
     * @param knownPersonIds person IDs currently enrolled on this device.
     */
    fun isOwnedByThisInstall(
        fileToken: String?,
        installToken: String?,
        backupPersonIds: List<String>,
        knownPersonIds: Set<String>,
    ): Boolean {
        // A file that carries a token is judged on that alone. Falling through to the ID check
        // when the token *mismatches* would be a downgrade a forger could aim for: they cannot
        // guess a 256-bit token, but a backup of the victim's own data has their person IDs in it.
        if (fileToken != null) {
            return installToken != null && constantTimeEquals(fileToken, installToken)
        }
        // Pre-v3 files predate the token. Person IDs are random UUIDs minted on this device, so a
        // stranger's backup cannot contain one — but a device with nobody enrolled has nothing to
        // match against, which is exactly the gap the token was added to close.
        if (knownPersonIds.isEmpty()) return false
        return backupPersonIds.any { it.isNotEmpty() && it in knownPersonIds }
    }

    /** Length-constant comparison, so a wrong token can't be narrowed down by timing. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ab = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        var result = ab.size xor bb.size
        for (i in 0 until maxOf(ab.size, bb.size)) {
            val x = if (i < ab.size) ab[i].toInt() else 0
            val y = if (i < bb.size) bb[i].toInt() else 0
            result = result or (x xor y)
        }
        return result == 0
    }
}

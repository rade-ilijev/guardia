package com.guardia.app.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The three PIN roles. */
enum class PinType { REAL, DECOY, PANIC }

/**
 * PIN hashing. New PINs are stretched with PBKDF2-HMAC-SHA256 (many iterations) so a stolen hash
 * can't be brute-forced quickly despite the small PIN space. Older salted-SHA-256 hashes are still
 * verifiable so existing users are never locked out; they upgrade transparently the next time the
 * PIN is changed.
 *
 * Stored format for new hashes: "v2$<iterations>$<base64-derived-key>".
 */
object PinManager {

    private const val PREFIX_V2 = "v2"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256

    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    /**
     * Alphabet for recovery codes: digits and uppercase letters with the pairs people mistranscribe
     * removed — no 0/O, no 1/I/L, no 8/B, no 5/S, no 2/Z. A code is written on paper and typed back
     * months later, often by someone reading their own handwriting, so ambiguity is a real failure
     * mode rather than a theoretical one.
     */
    private const val CODE_ALPHABET = "34679ACDEFGHJKMNPQRTUVWXY"

    /** Characters per group, and groups per code: 12 characters total. */
    private const val CODE_GROUP = 4
    private const val CODE_GROUPS = 3

    /**
     * Generates a recovery code such as `AH7K-M3PQ-XR49`.
     *
     * 12 characters from a 25-symbol alphabet is about 56 bits of entropy — far beyond a 6-digit
     * PIN, which matters because this credential is not rate-limited by the same lockout the PIN
     * uses and is meant to survive being written down and stored somewhere less careful.
     */
    fun newRecoveryCode(): String {
        val random = SecureRandom()
        return (0 until CODE_GROUPS).joinToString("-") {
            buildString {
                repeat(CODE_GROUP) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
            }
        }
    }

    /**
     * Canonical form for hashing and comparison: uppercase, with every character outside the
     * alphabet dropped. The user can type it with or without dashes, in either case, with stray
     * spaces from a paste — all of those verify.
     *
     * Upper-cased in [Locale.ROOT], never the device's locale. A canonical form that changes with
     * the phone's language is a credential that stops matching when someone travels or switches
     * language: in Turkish, `"i".uppercase()` is `İ`, not `I`. The stored hash was computed from
     * this function's output, so the mapping has to be the same everywhere, forever.
     */
    fun normalizeRecoveryCode(input: String): String =
        input.uppercase(Locale.ROOT).filter { it in CODE_ALPHABET }

    /** True when [input] could be a complete code, so the UI knows when to attempt verification. */
    fun looksLikeRecoveryCode(input: String): Boolean =
        normalizeRecoveryCode(input).length == CODE_GROUP * CODE_GROUPS

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /**
     * A 256-bit random opaque identifier. Used for the install token that Guardia embeds in the
     * backups it exports; unlike a salt it is a secret, so it is generated at full key length.
     */
    fun newToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /** Produces a hash for storage using the current (PBKDF2) scheme. */
    fun hash(pin: String, salt: String): String =
        "$PREFIX_V2\$$PBKDF2_ITERATIONS\$${pbkdf2(pin, salt, PBKDF2_ITERATIONS)}"

    /** Verifies [pin] against a [stored] hash of either the current or the legacy scheme. */
    fun verify(pin: String, salt: String, stored: String): Boolean {
        if (stored.startsWith("$PREFIX_V2\$")) {
            val parts = stored.split('$')
            val iterations = parts.getOrNull(1)?.toIntOrNull() ?: return false
            val expected = parts.getOrNull(2) ?: return false
            return constantTimeEquals(pbkdf2(pin, salt, iterations), expected)
        }
        // Legacy: salted SHA-256.
        return constantTimeEquals(legacySha256(pin, salt), stored)
    }

    private fun pbkdf2(pin: String, salt: String, iterations: Int): String {
        val spec = PBEKeySpec(pin.toCharArray(), decoder.decode(salt), iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return encoder.encodeToString(key)
    }

    private fun legacySha256(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(decoder.decode(salt))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(digest)
    }

    /** Length-constant string comparison to avoid leaking match position via timing. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ab = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        var result = ab.size xor bb.size
        val max = maxOf(ab.size, bb.size)
        for (i in 0 until max) {
            val x = if (i < ab.size) ab[i].toInt() else 0
            val y = if (i < bb.size) bb[i].toInt() else 0
            result = result or (x xor y)
        }
        return result == 0
    }
}

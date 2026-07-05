package com.guardia.app.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
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

    fun newSalt(): String {
        val bytes = ByteArray(16)
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

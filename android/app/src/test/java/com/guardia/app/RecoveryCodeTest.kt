package com.guardia.app

import com.guardia.app.core.security.PinManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recovery codes are the second credential that opens Guardia, so they get the same scrutiny as
 * PINs: shape, entropy, the normalisation users rely on when typing one off paper, and the fact
 * that they verify through the same hashing as everything else.
 */
class RecoveryCodeTest {

    @Test
    fun newRecoveryCode_hasTheAdvertisedShape() {
        val code = PinManager.newRecoveryCode()
        // Three groups of four, dash-separated: AH7K-M3PQ-XR49
        assertTrue("unexpected shape: $code", Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(code))
    }

    @Test
    fun newRecoveryCode_avoidsCharactersPeopleMistranscribe() {
        // 0/O, 1/I/L, 8/B, 5/S and 2/Z are the pairs that get confused reading handwriting back.
        val banned = charArrayOf('0', 'O', '1', 'I', 'L', '8', 'B', '5', 'S', '2', 'Z')
        repeat(200) {
            val code = PinManager.newRecoveryCode()
            banned.forEach { c ->
                assertFalse("$code contains the ambiguous character $c", code.contains(c))
            }
        }
    }

    @Test
    fun newRecoveryCode_isNotPredictable() {
        val codes = List(200) { PinManager.newRecoveryCode() }
        // Any repeat across 200 draws from a ~56-bit space means the generator isn't random.
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun normalize_acceptsHoweverTheUserTypesIt() {
        val code = "AH7K-M3PQ-XR49"
        val canonical = PinManager.normalizeRecoveryCode(code)
        assertEquals(canonical, PinManager.normalizeRecoveryCode("ah7k-m3pq-xr49"))
        assertEquals(canonical, PinManager.normalizeRecoveryCode("AH7KM3PQXR49"))
        assertEquals(canonical, PinManager.normalizeRecoveryCode("  AH7K M3PQ XR49  "))
        assertEquals(canonical, PinManager.normalizeRecoveryCode("AH7K–M3PQ–XR49")) // pasted en-dashes
    }

    @Test
    fun normalize_stripsSeparatorsAndKeepsTwelveCharacters() {
        assertEquals(12, PinManager.normalizeRecoveryCode(PinManager.newRecoveryCode()).length)
    }

    @Test
    fun looksLikeRecoveryCode_onlyTrueWhenComplete() {
        val code = PinManager.newRecoveryCode()
        assertTrue(PinManager.looksLikeRecoveryCode(code))
        assertFalse(PinManager.looksLikeRecoveryCode(code.dropLast(1)))
        assertFalse(PinManager.looksLikeRecoveryCode(""))
        // Extra characters are not a complete code either — verification must not fire on them.
        assertFalse(PinManager.looksLikeRecoveryCode(code + "X"))
    }

    @Test
    fun hashedCode_verifiesInAnyTypedForm() {
        val code = PinManager.newRecoveryCode()
        val salt = PinManager.newSalt()
        val stored = PinManager.hash(PinManager.normalizeRecoveryCode(code), salt)

        assertTrue(PinManager.verify(PinManager.normalizeRecoveryCode(code), salt, stored))
        assertTrue(PinManager.verify(PinManager.normalizeRecoveryCode(code.lowercase()), salt, stored))
        assertTrue(PinManager.verify(PinManager.normalizeRecoveryCode(code.replace("-", "")), salt, stored))
    }

    @Test
    fun hashedCode_rejectsAnyOtherCode() {
        val salt = PinManager.newSalt()
        val stored = PinManager.hash(PinManager.normalizeRecoveryCode(PinManager.newRecoveryCode()), salt)
        repeat(20) {
            val other = PinManager.normalizeRecoveryCode(PinManager.newRecoveryCode())
            assertFalse(PinManager.verify(other, salt, stored))
        }
    }

    @Test
    fun sameCodeUnderDifferentSaltsHashesDifferently() {
        // The recovery code gets its own salt rather than sharing the PIN's; rotating one must not
        // change the derivation of the other.
        val code = PinManager.normalizeRecoveryCode(PinManager.newRecoveryCode())
        assertNotEquals(
            PinManager.hash(code, PinManager.newSalt()),
            PinManager.hash(code, PinManager.newSalt()),
        )
    }

    // -- Install token -------------------------------------------------------------------------
    // The token embedded in exported backups. It is what separates "restore from backup" as a
    // recovery route from a bypass, so it has to be full-length and genuinely unpredictable: a
    // guessable token would let anyone's backup reset anyone's PIN.

    @Test
    fun newToken_is256BitsOfBase64() {
        val decoded = java.util.Base64.getDecoder().decode(PinManager.newToken())
        assertEquals(32, decoded.size)
    }

    @Test
    fun newToken_neverRepeats() {
        val seen = HashSet<String>()
        repeat(500) { assertTrue("token repeated", seen.add(PinManager.newToken())) }
    }

    @Test
    fun normalisationIsIdenticalInEveryLocale() {
        // Turkish maps i/I differently from every other language, which is how locale-dependent
        // case folding gets into shipped code unnoticed. Today's alphabet happens to contain no I,
        // so this passes either way — it is here to fail the day someone adds one back, because a
        // canonical form that changes with the phone's language is a credential that stops
        // verifying when the user switches language.
        val original = java.util.Locale.getDefault()
        try {
            val inputs = listOf(
                PinManager.newRecoveryCode(),
                PinManager.newRecoveryCode().lowercase(java.util.Locale.ROOT),
                "ii II \u0131\u0131 \u0130\u0130 ah7k-m3pq-xr49",
                "  a h 7 k \u2014 M3PQ \u2013 xr49  ",
            )
            for (input in inputs) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
                val turkish = PinManager.normalizeRecoveryCode(input)
                java.util.Locale.setDefault(java.util.Locale.US)
                val english = PinManager.normalizeRecoveryCode(input)
                assertEquals("locale changed the canonical form of \"$input\"", english, turkish)
            }
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun newToken_isLongerThanASalt() {
        // Salts are public values and only need to be unique; the token is a secret and is sized
        // as one. Keeping them different lengths makes the distinction visible in stored prefs.
        assertNotEquals(PinManager.newSalt().length, PinManager.newToken().length)
    }
}

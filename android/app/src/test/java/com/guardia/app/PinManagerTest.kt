package com.guardia.app

import com.guardia.app.core.security.PinManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PinManagerTest {

    @Test
    fun hash_usesPbkdf2V2Format() {
        val salt = PinManager.newSalt()
        val hash = PinManager.hash("1234", salt)
        val parts = hash.split('$')
        assertTrue(hash.startsWith("v2\$"))
        assertTrue(parts.size == 3)
        assertTrue(parts[1].toInt() >= 100_000)
    }

    @Test
    fun verify_acceptsCorrectPin() {
        val salt = PinManager.newSalt()
        val hash = PinManager.hash("4821", salt)
        assertTrue(PinManager.verify("4821", salt, hash))
    }

    @Test
    fun verify_rejectsWrongPin() {
        val salt = PinManager.newSalt()
        val hash = PinManager.hash("4821", salt)
        assertFalse(PinManager.verify("4822", salt, hash))
        assertFalse(PinManager.verify("48210", salt, hash))
        assertFalse(PinManager.verify("", salt, hash))
    }

    @Test
    fun newSalt_isRandom() {
        assertNotEquals(PinManager.newSalt(), PinManager.newSalt())
    }

    @Test
    fun verify_acceptsLegacySha256Hash() {
        // Reproduce the old salted-SHA-256 scheme to prove existing PINs still verify.
        val salt = PinManager.newSalt()
        val legacy = legacySha256("9090", salt)
        assertTrue(PinManager.verify("9090", salt, legacy))
        assertFalse(PinManager.verify("9091", salt, legacy))
    }

    private fun legacySha256(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(Base64.getDecoder().decode(salt))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}

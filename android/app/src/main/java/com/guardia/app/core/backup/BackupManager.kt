package com.guardia.app.core.backup

import android.util.Base64
import com.guardia.app.data.db.FaceSampleEntity
import com.guardia.app.data.db.PersonDao
import com.guardia.app.data.db.PersonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Portable, password-protected backup of enrolled people and their face embeddings.
 *
 * Format: "GBK1" magic + 16-byte salt + 12-byte IV + AES-256-GCM ciphertext of a JSON payload.
 * Encryption is derived from the user's password (PBKDF2WithHmacSHA256), so a backup can be
 * restored on any device/install - unlike the device-keystore encryption used for media.
 */
@Singleton
class BackupManager @Inject constructor(
    private val dao: PersonDao,
) {
    private val magic = "GBK1".toByteArray(Charsets.US_ASCII)

    sealed interface ImportResult {
        data class Success(val people: Int, val samples: Int) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val people = dao.allPeople()
        val samples = dao.allSamples()
        val payload = JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("people", JSONArray().apply {
                people.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        put("enabled", p.enabled)
                    })
                }
            })
            put("samples", JSONArray().apply {
                samples.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("personId", s.personId)
                        put("embedding", Base64.encodeToString(s.embedding, Base64.NO_WRAP))
                        put("quality", s.quality.toDouble())
                        put("createdAt", s.createdAt)
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        }
        val cipherText = cipher.doFinal(payload)
        magic + salt + iv + cipherText
    }

    suspend fun import(data: ByteArray, password: CharArray, replace: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                require(data.size > magic.size + 28) { "File is too small or corrupt." }
                require(data.copyOfRange(0, magic.size).contentEquals(magic)) { "Not a Guardia backup file." }
                var off = magic.size
                val salt = data.copyOfRange(off, off + 16); off += 16
                val iv = data.copyOfRange(off, off + 12); off += 12
                val cipherText = data.copyOfRange(off, data.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                }
                val json = JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))

                if (replace) {
                    dao.allPeople().forEach { dao.deletePerson(it.id) }
                }
                val peopleArr = json.getJSONArray("people")
                var peopleCount = 0
                for (i in 0 until peopleArr.length()) {
                    val o = peopleArr.getJSONObject(i)
                    dao.insertPerson(
                        PersonEntity(
                            id = o.getString("id"),
                            name = o.optString("name", "Unnamed"),
                            photoPath = null,
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            enabled = o.optBoolean("enabled", true),
                        )
                    )
                    peopleCount++
                }
                val sampleArr = json.getJSONArray("samples")
                val samples = ArrayList<FaceSampleEntity>(sampleArr.length())
                for (i in 0 until sampleArr.length()) {
                    val o = sampleArr.getJSONObject(i)
                    samples.add(
                        FaceSampleEntity(
                            id = o.getString("id"),
                            personId = o.getString("personId"),
                            embedding = Base64.decode(o.getString("embedding"), Base64.NO_WRAP),
                            quality = o.optDouble("quality", 1.0).toFloat(),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        )
                    )
                }
                if (samples.isNotEmpty()) dao.insertSamples(samples)
                ImportResult.Success(peopleCount, samples.size) as ImportResult
            }.getOrElse { e ->
                val msg = when (e) {
                    is javax.crypto.AEADBadTagException -> "Wrong password or corrupted file."
                    else -> e.message ?: "Could not restore this backup."
                }
                ImportResult.Error(msg)
            }
        }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}

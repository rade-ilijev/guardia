package com.guardia.app.core.backup

import android.util.Base64
import com.guardia.app.data.db.FaceSampleEntity
import com.guardia.app.data.db.NegativeFaceEntity
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
 *
 * Payload version 3 adds an "owner" field: the exporting install's token. It is not part of
 * restoring - [import] ignores it - but it lets [verifyOwner] tell the owner's own backup from a
 * stranger's, which is what makes the file usable as a PIN-recovery credential on the lock screen.
 */
@Singleton
class BackupManager @Inject constructor(
    private val dao: PersonDao,
    private val people: com.guardia.app.data.PeopleRepository,
    private val prefs: com.guardia.app.data.AppPreferences,
) {
    private val magic = "GBK1".toByteArray(Charsets.US_ASCII)

    sealed interface ImportResult {
        data class Success(val people: Int, val samples: Int) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    /** Outcome of asking whether a backup file was produced by *this* install. */
    sealed interface OwnerCheck {
        /** The file came from this install, so it can authorise a PIN reset. */
        data object Owned : OwnerCheck

        /** It decrypted, but it is somebody else's backup. */
        data object Foreign : OwnerCheck

        data class Error(val message: String) : OwnerCheck
    }

    suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val people = dao.allPeople()
        val samples = dao.allSamples()
        val negatives = dao.allNegatives()
        val payload = JSONObject().apply {
            put("version", 3)
            put("createdAt", System.currentTimeMillis())
            // Proof of origin, not a credential: see AppPreferences.installToken. It is what lets a
            // locked-out owner reset their PIN from this file without handing the same power to
            // anyone who can make a backup of their own.
            put("owner", prefs.installToken())
            put("people", JSONArray().apply {
                people.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        put("enabled", p.enabled)
                        // Critical: without this, a block-listed (unauthorized) person restores as an
                        // authorized owner and would then PASS face checks.
                        put("blocked", p.blocked)
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
                        // Preserve which pipeline produced the embedding, else v2 faces restore as
                        // legacy v0 and the app wrongly prompts a re-enroll.
                        put("modelVersion", s.modelVersion)
                    })
                }
            })
            put("negatives", JSONArray().apply {
                negatives.forEach { n ->
                    put(JSONObject().apply {
                        put("id", n.id)
                        n.personId?.let { put("personId", it) }
                        put("embedding", Base64.encodeToString(n.embedding, Base64.NO_WRAP))
                        put("createdAt", n.createdAt)
                        put("modelVersion", n.modelVersion)
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

    /**
     * Decides whether [data] is a backup this install produced, without importing anything.
     *
     * Used by the lock screen: someone who has forgotten their PIN still has all their faces on the
     * device, so there is nothing to restore — the file's job there is to prove ownership.
     *
     * Two things can prove it. Backups from version 3 onward carry this install's token. Older
     * files do not, so they fall back to overlap of person IDs: those are random UUIDs minted on
     * this device, and a stranger's backup cannot contain one. The fallback needs at least one
     * enrolled person, which is why the token exists at all.
     */
    suspend fun verifyOwner(data: ByteArray, password: CharArray): OwnerCheck =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = decrypt(data, password)
                val arr = json.getJSONArray("people")
                val backupPersonIds = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) backupPersonIds.add(arr.getJSONObject(i).optString("id"))
                val owned = BackupOwnership.isOwnedByThisInstall(
                    fileToken = json.optString("owner", "").ifEmpty { null },
                    installToken = prefs.installTokenOrNull(),
                    backupPersonIds = backupPersonIds,
                    knownPersonIds = dao.allPeople().mapTo(HashSet()) { it.id },
                )
                if (owned) OwnerCheck.Owned else OwnerCheck.Foreign
            }.getOrElse { e -> OwnerCheck.Error(readableError(e, "Could not read this backup.")) }
        }

    suspend fun import(data: ByteArray, password: CharArray, replace: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = decrypt(data, password)

                if (replace) {
                    // Delete each person *with* its samples and negatives — the entities declare no
                    // foreign keys, so deleting the person alone would orphan those rows (they'd
                    // bloat the DB, re-export into future backups, and skew the re-enroll prompt,
                    // which counts total samples).
                    dao.allPeople().forEach { p ->
                        dao.deleteSamples(p.id)
                        dao.deleteNegativesFor(p.id)
                        dao.deletePerson(p.id)
                    }
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
                            blocked = o.optBoolean("blocked", false),
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
                            modelVersion = o.optInt("modelVersion", 0),
                        )
                    )
                }
                if (samples.isNotEmpty()) dao.insertSamples(samples)
                // Restore declined look-alikes (negatives) so recognition stays as sharp as before.
                val negArr = json.optJSONArray("negatives")
                if (negArr != null) {
                    val negatives = ArrayList<NegativeFaceEntity>(negArr.length())
                    for (i in 0 until negArr.length()) {
                        val o = negArr.getJSONObject(i)
                        negatives.add(
                            NegativeFaceEntity(
                                id = o.getString("id"),
                                personId = if (o.has("personId")) o.getString("personId") else null,
                                embedding = Base64.decode(o.getString("embedding"), Base64.NO_WRAP),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                modelVersion = o.optInt("modelVersion", 0),
                            )
                        )
                    }
                    if (negatives.isNotEmpty()) dao.insertNegatives(negatives)
                }
                // We wrote via the DAO directly, so drop the recognizer's in-memory cache or guarding
                // wouldn't see the restored faces until the next app restart.
                people.invalidate()
                ImportResult.Success(peopleCount, samples.size) as ImportResult
            }.getOrElse { e -> ImportResult.Error(readableError(e, "Could not restore this backup.")) }
        }

    /** Parses the container and decrypts the payload. Throws on a bad file or wrong password. */
    private fun decrypt(data: ByteArray, password: CharArray): JSONObject {
        require(data.size > magic.size + 28) { "File is too small or corrupt." }
        require(data.copyOfRange(0, magic.size).contentEquals(magic)) { "Not a Guardia backup file." }
        var off = magic.size
        val salt = data.copyOfRange(off, off + 16); off += 16
        val iv = data.copyOfRange(off, off + 12); off += 12
        val cipherText = data.copyOfRange(off, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        }
        return JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
    }

    private fun readableError(e: Throwable, fallback: String): String = when (e) {
        is javax.crypto.AEADBadTagException -> "Wrong password or corrupted file."
        else -> e.message ?: fallback
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}

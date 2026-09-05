package com.guardia.app.data

import com.guardia.app.core.security.CryptoManager
import com.guardia.app.data.db.IntruderCaptureEntity
import com.guardia.app.data.db.IntruderDao
import com.guardia.app.domain.model.IntruderCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntruderRepository @Inject constructor(
    private val dao: IntruderDao,
    private val crypto: CryptoManager,
) {
    val captures: Flow<List<IntruderCapture>> = dao.observeCaptures().map { list ->
        list.map { IntruderCapture(it.id, it.photoPath, it.source, it.timestamp) }
    }

    /** Encrypts the JPEG bytes and records the capture. Returns the encrypted path. */
    suspend fun saveCapture(jpegBytes: ByteArray, source: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Blocking Keystore file encryption — keep it off the caller's thread so a lock/screen-off
            // on the caller can never truncate the write.
            val path = crypto.saveEncrypted(jpegBytes, "intruders")
            dao.insert(
                IntruderCaptureEntity(
                    id = UUID.randomUUID().toString(),
                    photoPath = path,
                    source = source,
                    timestamp = System.currentTimeMillis(),
                )
            )
            path
        }

    fun decrypt(path: String): ByteArray? = crypto.readEncrypted(path)

    suspend fun delete(id: String) {
        dao.byId(id)?.let { crypto.delete(it.photoPath) }
        dao.delete(id)
    }

    /**
     * Deletes captures older than [days], oldest evidence first. Returns how many went.
     *
     * The encrypted file is removed before its row, so a crash between the two leaves an orphaned
     * file rather than a row pointing at nothing — the gallery stays consistent either way, and
     * "Clear unused face photos" sweeps the orphan up.
     */
    suspend fun purgeOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cutoff = now - days * 24L * 60 * 60 * 1000
        val stale = dao.olderThan(cutoff)
        stale.forEach {
            crypto.delete(it.photoPath)
            dao.delete(it.id)
        }
        return stale.size
    }

    /** Deletes every capture, removing the encrypted files from disk before clearing the rows. */
    suspend fun clear() {
        dao.all().forEach { crypto.delete(it.photoPath) }
        dao.clear()
    }
}

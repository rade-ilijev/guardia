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
    suspend fun saveCapture(jpegBytes: ByteArray, source: String): String {
        val path = crypto.saveEncrypted(jpegBytes, "intruders")
        dao.insert(
            IntruderCaptureEntity(
                id = UUID.randomUUID().toString(),
                photoPath = path,
                source = source,
                timestamp = System.currentTimeMillis(),
            )
        )
        return path
    }

    fun decrypt(path: String): ByteArray? = crypto.readEncrypted(path)

    suspend fun delete(id: String) {
        dao.byId(id)?.let { crypto.delete(it.photoPath) }
        dao.delete(id)
    }

    suspend fun clear() = dao.clear()
}

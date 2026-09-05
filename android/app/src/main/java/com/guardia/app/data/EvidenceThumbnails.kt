package com.guardia.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes encrypted intruder captures for display, at the size they will actually be shown.
 *
 * Every thumbnail in the app used to go through the same path: decrypt the whole JPEG, decode it
 * at its full 1280×720 (about 3.7 MB of pixels), and hand that to a 46dp box. The Intruders grid
 * does this for every tile, and again for every tile that scrolls back into view — which on a
 * phone with a few dozen captures is tens of megabytes of bitmap churn per scroll and a visible
 * stutter as each one lands.
 *
 * Two fixes here:
 *  - **Subsampled decode.** The bounds are read first (no pixels), then the JPEG is decoded with
 *    the largest power-of-two `inSampleSize` that still leaves the image at least [targetPx]
 *    across. A 46dp tile on a 3× screen wants ~140px; from 1280 wide that is an 8× subsample, and
 *    a 64× smaller allocation.
 *  - **A small LRU.** Decoded thumbnails are kept in memory, keyed by path and size, so scrolling
 *    back never decrypts twice. Capped at [CACHE_BYTES]; at thumbnail sizes that is hundreds of
 *    images, and the cache is only ever RAM — nothing decrypted is written anywhere.
 *
 * The full-size decode for the evidence viewer stays uncached and unsampled; that is the one
 * place the pixels are the point.
 */
@Singleton
class EvidenceThumbnails @Inject constructor(
    private val intruders: IntruderRepository,
) {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** A thumbnail of the capture at [path], at least [targetPx] on its shorter side. */
    suspend fun thumbnail(path: String, targetPx: Int): ImageBitmap? {
        val key = "$path@$targetPx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = intruders.decrypt(path) ?: return@withContext null
            decodeSampled(bytes, targetPx)?.asImageBitmap()?.also { cache.put(key, it) }
        }
    }

    /** The full-resolution capture, for the viewer. Not cached. */
    suspend fun full(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val bytes = intruders.decrypt(path) ?: return@withContext null
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }

    /** Drops every cached thumbnail. Called when captures are deleted so stale tiles can't linger. */
    fun evictAll() = cache.evictAll()

    private fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val shorter = minOf(bounds.outWidth, bounds.outHeight)
        if (shorter <= 0) return null
        var sample = 1
        // Largest power of two that keeps the shorter side at or above the target.
        while (shorter / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // opaque JPEG; half the bytes of ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()

    private companion object {
        /** 6 MB of decoded thumbnails — roughly 300 tiles at grid size. */
        const val CACHE_BYTES = 6 * 1024 * 1024
    }
}

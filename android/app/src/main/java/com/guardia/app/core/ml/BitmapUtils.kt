package com.guardia.app.core.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object BitmapUtils {

    fun toJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Mean perceived luminance (0..255).
     *
     * This used to walk a 16x16 grid calling [Bitmap.getPixel] per sample: 256 separate JNI
     * round-trips, on the guard loop's hot path, several times per check. Scaling to a 16x16 bitmap
     * is one native operation, and reading it back is one bulk `getPixels`, so the same measurement
     * costs two JNI calls instead of 256. (The downscale also averages rather than point-samples,
     * which makes the reading slightly more faithful.)
     */
    fun averageLuminance(bitmap: Bitmap): Float {
        val pixels = grayGrid(bitmap, LUMA_GRID) ?: return 0f
        var sum = 0.0
        for (v in pixels) sum += v
        return (sum / pixels.size).toFloat()
    }

    /**
     * Relative sharpness of a face crop, as the mean absolute Laplacian response (0..~255).
     *
     * A motion-blurred or out-of-focus face produces an embedding that sits near the middle of the
     * embedding space — close to *everyone*, which is precisely how a stranger gets accepted. There
     * is no cheap absolute blur metric, but the Laplacian's spread is a good relative one: a sharp
     * face scores several times higher than a blurred one at the same resolution.
     *
     * Measured on a fixed [SHARPNESS_GRID]-square downscale so the number is comparable between a
     * close-up and a distant face, which raw-resolution Laplacian variance is not.
     */
    fun sharpness(bitmap: Bitmap): Float {
        val n = SHARPNESS_GRID
        val g = grayGrid(bitmap, n) ?: return 0f
        var sum = 0.0
        var count = 0
        for (y in 1 until n - 1) {
            val row = y * n
            for (x in 1 until n - 1) {
                val i = row + x
                // 4-neighbour Laplacian.
                val lap = 4f * g[i] - g[i - 1] - g[i + 1] - g[i - n] - g[i + n]
                sum += abs(lap)
                count++
            }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    /**
     * Downscales to [n] x [n] and returns perceived luminance per pixel. One native scale plus one
     * bulk pixel read; the caller gets a small float grid it can scan without touching the Bitmap.
     */
    private fun grayGrid(bitmap: Bitmap, n: Int): FloatArray? {
        if (bitmap.width < 2 || bitmap.height < 2) return null
        val small = runCatching { Bitmap.createScaledBitmap(bitmap, n, n, true) }.getOrNull() ?: return null
        val argb = IntArray(n * n)
        small.getPixels(argb, 0, n, 0, 0, n, n)
        if (small !== bitmap) small.recycle()
        return FloatArray(n * n) { i ->
            val c = argb[i]
            0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
    }

    /** Crops [rect] from [bitmap], clamped to bounds; returns null if degenerate. */
    fun crop(bitmap: Bitmap, rect: Rect): Bitmap? {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w < 2 || h < 2) return null
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }

    private const val LUMA_GRID = 16
    private const val SHARPNESS_GRID = 48
}

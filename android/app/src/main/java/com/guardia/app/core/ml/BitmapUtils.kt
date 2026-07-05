package com.guardia.app.core.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import java.io.ByteArrayOutputStream

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
     * Mean perceived luminance (0..255) over a sparse grid of pixels. Used to detect very low light,
     * where face embeddings become unreliable and prone to false matches.
     */
    fun averageLuminance(bitmap: Bitmap): Float {
        val cols = 16
        val rows = 16
        val stepX = (bitmap.width / cols).coerceAtLeast(1)
        val stepY = (bitmap.height / rows).coerceAtLeast(1)
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += 0.299 * r + 0.587 * g + 0.114 * b
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else (sum / count).toFloat()
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
}

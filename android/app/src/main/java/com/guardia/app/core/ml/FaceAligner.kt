package com.guardia.app.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Produces a canonical, aligned face crop so that every embedding — whether captured during
 * enrollment, imported from the gallery, or seen live by the guard loop — is framed the same way.
 *
 * When both eye landmarks are present the crop is rotated so the eyes are level and scaled so the
 * inter-ocular distance is constant; this cancels head tilt and camera distance, which is the single
 * biggest factor in telling similar-looking people apart. If landmarks are missing it falls back to a
 * margin-padded bounding-box crop. Identical preprocessing on both the enrolled face and the probe is
 * what makes a higher match threshold safe.
 */
object FaceAligner {

    private const val OUT_SIZE = 160

    /** Full face width as a multiple of inter-ocular distance (≈ adds forehead/chin + side margin). */
    private const val FACE_WIDTH_FROM_EYES = 2.5f

    /** Vertical placement of the eye line within the output (fraction from top). */
    private const val EYE_LINE_Y = 0.42f

    /** Extra padding around the bounding box used by the no-landmark fallback. */
    private const val BOX_MARGIN = 0.25f

    fun align(src: Bitmap, face: Face, outSize: Int = OUT_SIZE): Bitmap {
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (left != null && right != null) {
            val dx = right.x - left.x
            val dy = right.y - left.y
            val eyeDist = hypot(dx, dy)
            if (eyeDist >= MIN_EYE_DISTANCE) {
                return runCatching { alignByEyes(src, left.x, left.y, right.x, right.y, eyeDist, outSize) }
                    .getOrElse { marginCrop(src, face.boundingBox, outSize) }
            }
        }
        return marginCrop(src, face.boundingBox, outSize)
    }

    private fun alignByEyes(
        src: Bitmap,
        leftX: Float, leftY: Float,
        rightX: Float, rightY: Float,
        eyeDist: Float,
        outSize: Int,
    ): Bitmap {
        val cx = (leftX + rightX) / 2f
        val cy = (leftY + rightY) / 2f
        val angle = Math.toDegrees(atan2((rightY - leftY).toDouble(), (rightX - leftX).toDouble())).toFloat()
        val faceWidth = eyeDist * FACE_WIDTH_FROM_EYES
        val scale = outSize / faceWidth
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postTranslate(-cx, -cy)
            postRotate(-angle)
            postScale(scale, scale)
            postTranslate(outSize / 2f, outSize * EYE_LINE_Y)
        }
        Canvas(out).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun marginCrop(src: Bitmap, rect: Rect, outSize: Int): Bitmap {
        val w = rect.width()
        val h = rect.height()
        val left = (rect.left - w * BOX_MARGIN).toInt().coerceIn(0, src.width - 1)
        val top = (rect.top - h * BOX_MARGIN).toInt().coerceIn(0, src.height - 1)
        val right = (rect.right + w * BOX_MARGIN).toInt().coerceIn(left + 1, src.width)
        val bottom = (rect.bottom + h * BOX_MARGIN).toInt().coerceIn(top + 1, src.height)
        val cropped = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
    }

    private const val MIN_EYE_DISTANCE = 8f
}

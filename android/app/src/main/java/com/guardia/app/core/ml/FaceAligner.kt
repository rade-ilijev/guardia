package com.guardia.app.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Produces a canonical, aligned face crop so that every embedding — whether captured during
 * enrollment, imported from the gallery, or seen live by the guard loop — is framed the same way.
 *
 * Identical framing on the enrolled face and on the probe is what makes a high match threshold safe,
 * so this is the highest-leverage stage in the whole pipeline: an embedder can only tell two people
 * apart if it is looking at the same part of both their faces.
 *
 * **Scale is estimated from two landmark distances, not one.** The previous version derived the crop
 * size purely from inter-ocular distance. That distance is foreshortened by `cos(yaw)`, so a head
 * turned 30 degrees was cropped ~15% too tight and the face was pushed out of frame — exactly the
 * situation (glancing at the phone from an angle) where the owner most wants to be recognised. Eye
 * separation and eye-to-mouth distance fail under *opposite* rotations — yaw squashes the first,
 * pitch squashes the second — so taking the larger of the two estimates keeps the crop stable
 * through both. See [FACE_WIDTH_FROM_EYES] and [FACE_WIDTH_FROM_EYE_MOUTH] for the canonical ratios.
 *
 * **Roll comes from two axes.** Eye-line angle alone is jittery when one eye is partly occluded; the
 * eye-centre-to-mouth-centre axis is a slower, steadier estimate of the same rotation. Blending them
 * removes most of the frame-to-frame wobble that was making consecutive embeddings of a *stationary*
 * face differ.
 */
object FaceAligner {

    private const val OUT_SIZE = 160

    /**
     * Full face width as a multiple of inter-ocular distance. In a canonical frontal face the eyes
     * sit about 0.36 of the face width apart, and the crop adds forehead/chin margin on top.
     */
    private const val FACE_WIDTH_FROM_EYES = 2.5f

    /**
     * Full face width as a multiple of eye-centre-to-mouth-centre distance. That span is roughly
     * 0.42 of the face width, and unlike eye separation it survives yaw unchanged.
     */
    private const val FACE_WIDTH_FROM_EYE_MOUTH = 2.15f

    /** Vertical placement of the eye line within the output (fraction from top). */
    private const val EYE_LINE_Y = 0.42f

    /** Extra padding around the bounding box used by the no-landmark fallback. */
    private const val BOX_MARGIN = 0.25f

    /** How much the eye-to-mouth axis contributes to the roll estimate (the eye line carries the rest). */
    private const val AXIS_ANGLE_WEIGHT = 0.3f

    fun align(src: Bitmap, face: Face, outSize: Int = OUT_SIZE): Bitmap {
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (left == null || right == null) return marginCrop(src, face.boundingBox, outSize)

        val eyeDist = hypot(right.x - left.x, right.y - left.y)
        if (eyeDist < MIN_EYE_DISTANCE) return marginCrop(src, face.boundingBox, outSize)

        val eyeCx = (left.x + right.x) / 2f
        val eyeCy = (left.y + right.y) / 2f
        val eyeAngle = Math.toDegrees(atan2((right.y - left.y).toDouble(), (right.x - left.x).toDouble())).toFloat()

        // Mouth corners give the second scale reference and the second roll reference. Either may be
        // missing (steep angles, occlusion), in which case we fall back to eyes-only behaviour.
        val mouthL = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthR = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val mouthCx: Float?
        val mouthCy: Float?
        if (mouthL != null && mouthR != null) {
            mouthCx = (mouthL.x + mouthR.x) / 2f
            mouthCy = (mouthL.y + mouthR.y) / 2f
        } else if (mouthBottom != null) {
            mouthCx = mouthBottom.x
            mouthCy = mouthBottom.y
        } else {
            mouthCx = null
            mouthCy = null
        }

        var faceWidth = eyeDist * FACE_WIDTH_FROM_EYES
        var angle = eyeAngle

        if (mouthCx != null && mouthCy != null) {
            val eyeMouth = hypot(mouthCx - eyeCx, mouthCy - eyeCy)
            if (eyeMouth >= MIN_EYE_MOUTH_DISTANCE) {
                // Larger of the two estimates: whichever rotation is happening, one of them is still
                // seeing the face at close to its true size.
                faceWidth = maxOf(faceWidth, eyeMouth * FACE_WIDTH_FROM_EYE_MOUTH)

                // The eye->mouth axis points "down" the face; subtracting 90 degrees expresses it in
                // the same frame as the eye line so the two can be averaged.
                val axisAngle = Math.toDegrees(
                    atan2((mouthCy - eyeCy).toDouble(), (mouthCx - eyeCx).toDouble()),
                ).toFloat() - 90f
                // Only blend when the two agree to within a sane margin; a wild disagreement means one
                // landmark set is wrong, and the eye line is the more reliable of the two.
                if (abs(axisAngle - eyeAngle) <= MAX_AXIS_DISAGREEMENT) {
                    angle = eyeAngle * (1f - AXIS_ANGLE_WEIGHT) + axisAngle * AXIS_ANGLE_WEIGHT
                }
            }
        }

        // A crop wider than the source can only be padding, and a degenerate one is a bad detection.
        if (faceWidth < MIN_FACE_WIDTH) return marginCrop(src, face.boundingBox, outSize)

        return runCatching { warp(src, eyeCx, eyeCy, angle, faceWidth, outSize) }
            .getOrElse { marginCrop(src, face.boundingBox, outSize) }
    }

    /**
     * Similarity transform: translate the eye centre to the origin, undo the roll, scale so the face
     * occupies [outSize], then place the eye line at [EYE_LINE_Y] down the output.
     */
    private fun warp(
        src: Bitmap,
        eyeCx: Float,
        eyeCy: Float,
        angleDeg: Float,
        faceWidth: Float,
        outSize: Int,
    ): Bitmap {
        val scale = outSize / faceWidth
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postTranslate(-eyeCx, -eyeCy)
            postRotate(-angleDeg)
            postScale(scale, scale)
            postTranslate(outSize / 2f, outSize * EYE_LINE_Y)
        }
        Canvas(out).drawBitmap(src, matrix, WARP_PAINT)
        return out
    }

    /**
     * The version-2 alignment: eyes-only scale and eyes-only roll.
     *
     * Kept verbatim so faces enrolled before the two-axis change can still be compared against a
     * probe framed the way *they* were framed. Mixing alignments across a comparison shifts cosine
     * similarity by more than the acceptance margin, so an upgrade that silently changed framing
     * would lock existing owners out of their own phones — which is why [EmbeddingMath.VERSION] is
     * bumped alongside any edit to [align], and this function is never edited again.
     */
    fun alignV2(src: Bitmap, face: Face, outSize: Int = OUT_SIZE): Bitmap {
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (left != null && right != null) {
            val eyeDist = hypot(right.x - left.x, right.y - left.y)
            if (eyeDist >= MIN_EYE_DISTANCE) {
                val cx = (left.x + right.x) / 2f
                val cy = (left.y + right.y) / 2f
                val angle = Math.toDegrees(
                    atan2((right.y - left.y).toDouble(), (right.x - left.x).toDouble()),
                ).toFloat()
                return runCatching { warp(src, cx, cy, angle, eyeDist * FACE_WIDTH_FROM_EYES, outSize) }
                    .getOrElse { marginCrop(src, face.boundingBox, outSize) }
            }
        }
        return marginCrop(src, face.boundingBox, outSize)
    }

    private fun marginCrop(src: Bitmap, rect: Rect, outSize: Int): Bitmap {
        val w = rect.width()
        val h = rect.height()
        val left = (rect.left - w * BOX_MARGIN).toInt().coerceIn(0, src.width - 1)
        val top = (rect.top - h * BOX_MARGIN).toInt().coerceIn(0, src.height - 1)
        val right = (rect.right + w * BOX_MARGIN).toInt().coerceIn(left + 1, src.width)
        val bottom = (rect.bottom + h * BOX_MARGIN).toInt().coerceIn(top + 1, src.height)
        val cropped = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        val out = Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
        // The intermediate crop is a few hundred KB and was being dropped on the floor on every
        // frame that took this path. Both factory calls are allowed to hand back their source
        // unchanged, so only recycle what is genuinely an intermediate.
        if (out !== cropped && cropped !== src) cropped.recycle()
        return out
    }

    /** Invariant, and never mutated — a fresh one per warp was pure allocation. */
    private val WARP_PAINT = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private const val MIN_EYE_DISTANCE = 8f
    private const val MIN_EYE_MOUTH_DISTANCE = 10f
    private const val MIN_FACE_WIDTH = 24f
    private const val MAX_AXIS_DISAGREEMENT = 25f
}

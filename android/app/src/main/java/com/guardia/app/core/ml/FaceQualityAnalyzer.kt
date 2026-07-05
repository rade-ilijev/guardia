package com.guardia.app.core.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs

/** ML Kit face detection plus enrollment-quality scoring. */
@Singleton
class FaceQualityAnalyzer @Inject constructor() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    data class Quality(val ok: Boolean, val reason: String, val score: Float)

    /** Discrete head orientations used to guide multi-angle enrollment. */
    enum class HeadPose { CENTER, LEFT, RIGHT, UP, DOWN }

    /** Detects faces on an upright bitmap (rotation already applied). */
    suspend fun detect(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }

    /**
     * Classifies the head orientation from Euler angles, or null when it's between poses (still
     * turning). Yaw = left/right, pitch = up/down.
     */
    fun poseOf(face: Face): HeadPose? {
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        return when {
            abs(yaw) <= 12f && abs(pitch) <= 12f -> HeadPose.CENTER
            // Front camera is mirrored: a positive yaw corresponds to the user turning their head
            // to their left (and vice versa), so map the signs to match the on-screen prompt.
            yaw > 18f && abs(pitch) < 22f -> HeadPose.LEFT
            yaw < -18f && abs(pitch) < 22f -> HeadPose.RIGHT
            pitch > 14f && abs(yaw) < 22f -> HeadPose.UP
            pitch < -14f && abs(yaw) < 22f -> HeadPose.DOWN
            else -> null
        }
    }

    /**
     * Quality check that ignores head pose (used during guided multi-angle enrollment, where turned
     * faces are expected). Verifies the face is big enough, well-lit, and — for the front pose —
     * that the eyes are open.
     */
    fun assessBasics(face: Face, bitmap: Bitmap, requireEyesOpen: Boolean): Quality {
        val faceArea = face.boundingBox.width().toFloat() * face.boundingBox.height()
        val sizeRatio = faceArea / (bitmap.width.toFloat() * bitmap.height)
        if (sizeRatio < 0.07f) return Quality(false, "Move closer", sizeRatio)
        val luma = BitmapUtils.crop(bitmap, face.boundingBox)?.let { BitmapUtils.averageLuminance(it) } ?: 0f
        if (luma < MIN_ENROLL_LUMA) return Quality(false, "Too dark — find better lighting", 0f)
        if (requireEyesOpen) {
            val eyes = ((face.leftEyeOpenProbability ?: 1f) + (face.rightEyeOpenProbability ?: 1f)) / 2f
            if (eyes < 0.4f) return Quality(false, "Keep your eyes open", 0f)
        }
        return Quality(true, "Good", sizeRatio.coerceAtMost(0.5f) * 2f)
    }

    /** Scores a face for enrollment suitability (size, pose, eyes open). */
    fun assess(face: Face, bitmap: Bitmap): Quality {
        val faceArea = face.boundingBox.width().toFloat() * face.boundingBox.height()
        val frameArea = bitmap.width.toFloat() * bitmap.height
        val sizeRatio = faceArea / frameArea
        if (sizeRatio < 0.08f) return Quality(false, "Move closer", sizeRatio)

        // Reject dark enrollment frames: a low-light reference embedding matches almost anyone.
        val luma = BitmapUtils.crop(bitmap, face.boundingBox)?.let { BitmapUtils.averageLuminance(it) } ?: 0f
        if (luma < MIN_ENROLL_LUMA) return Quality(false, "Too dark — find better lighting", 0f)

        val yaw = abs(face.headEulerAngleY)
        val roll = abs(face.headEulerAngleZ)
        if (yaw > 22f) return Quality(false, "Look straight ahead", 0f)
        if (roll > 18f) return Quality(false, "Keep your head level", 0f)

        val leftEye = face.leftEyeOpenProbability ?: 1f
        val rightEye = face.rightEyeOpenProbability ?: 1f
        if (leftEye < 0.4f || rightEye < 0.4f) return Quality(false, "Keep your eyes open", 0f)

        val score = (sizeRatio.coerceAtMost(0.5f) * 2f) * 0.5f + ((leftEye + rightEye) / 2f) * 0.5f
        return Quality(true, "Good", score)
    }

    companion object {
        /** Minimum mean face luminance (0..255) accepted for enrollment. */
        const val MIN_ENROLL_LUMA = 60f
    }
}

package com.guardia.app.core.ml

import android.graphics.Bitmap

/**
 * On-device face pipeline: ML Kit detection -> face-count logic -> embedding
 * (MobileFaceNet/FaceNet) -> nearest-neighbor match against enrolled people.
 * Android exposes no face-recognition API, so recognition is our own AI.
 */
interface FacePipeline {

    enum class Outcome { MATCH, NO_MATCH, BLOCKED, NO_FACE, MULTIPLE_FACES, INCONCLUSIVE }

    /** Why a frame couldn't produce a recognition decision (only set when outcome is INCONCLUSIVE). */
    enum class InconclusiveReason { LOW_LIGHT, NO_ENROLLMENT, FACE_UNCLEAR }

    data class Analysis(
        val outcome: Outcome,
        val similarity: Float,
        val personName: String?,
        val personId: String? = null,
        val reason: InconclusiveReason? = null,
        /** Coarse on-device appearance estimate for an unrecognized/blocked face (else null). */
        val appearance: AppearanceAnalyzer.Appearance? = null,
        /** Liveness signals from the primary detected face (for the per-app blink challenge). */
        val eyesOpen: Float? = null,
        val headYaw: Float? = null,
        val headPitch: Float? = null,
    )

    /** Analyzes a captured frame. [rotationDegrees] is applied before detection. */
    suspend fun analyze(bitmap: Bitmap, rotationDegrees: Int, sensitivity: Float): Analysis
}

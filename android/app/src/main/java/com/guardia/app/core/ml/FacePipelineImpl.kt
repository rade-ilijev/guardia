package com.guardia.app.core.ml

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FacePipelineImpl @Inject constructor(
    private val quality: FaceQualityAnalyzer,
    private val embedder: FaceEmbedder,
    private val recognizer: FaceRecognizer,
    private val people: com.guardia.app.data.PeopleRepository,
    private val appearance: AppearanceAnalyzer,
    private val gender: GenderClassifier,
) : FacePipeline {

    override suspend fun analyze(bitmap: Bitmap, rotationDegrees: Int, sensitivity: Float): FacePipeline.Analysis {
        val upright = BitmapUtils.rotate(bitmap, rotationDegrees)
        val faces = quality.detect(upright)
        when {
            faces.isEmpty() -> return FacePipeline.Analysis(FacePipeline.Outcome.NO_FACE, 0f, null)
            faces.size > 1 -> return FacePipeline.Analysis(FacePipeline.Outcome.MULTIPLE_FACES, 0f, null)
        }
        val face = faces[0]
        val crop = BitmapUtils.crop(upright, face.boundingBox)
            ?: return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.FACE_UNCLEAR)
        // In very low light, embeddings are unreliable and can falsely match another person.
        // Refuse to make a recognition decision rather than risk a false accept. (Luminance is
        // measured on the raw crop so brightness normalization can't mask a genuinely dark scene.)
        val luma = BitmapUtils.averageLuminance(crop)
        if (luma < MIN_RECOGNITION_LUMA) {
            return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.LOW_LIGHT)
        }
        // In dim (but usable) light, raise the bar so a noisy embedding can't false-accept a stranger.
        val thresholdBoost = if (luma < DIM_LUMA)
            DIM_THRESHOLD_BOOST * ((DIM_LUMA - luma) / (DIM_LUMA - MIN_RECOGNITION_LUMA)) else 0f

        // Build one probe embedding per pipeline version present in the enrollment, so faces enrolled
        // before the upgrade are compared with the same (legacy) preprocessing they were stored with.
        val versions = people.usedModelVersions()
        if (versions.isEmpty()) {
            return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.NO_ENROLLMENT)
        }
        val probes = HashMap<Int, FloatArray>(versions.size)
        for (v in versions) {
            probes[v] = if (v == EmbeddingMath.VERSION) embedder.embed(FaceAligner.align(upright, face))
            else embedder.embedLegacy(crop)
        }
        val match = recognizer.identify(probes, sensitivity, thresholdBoost)
        // With no enrolled owner to compare against we can't authenticate anyone; treat as
        // "can't decide" so guarding never locks the owner out before any face is enrolled.
        if (match.noEnrolledOwners && !match.blocked) {
            return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.NO_ENROLLMENT)
        }
        val outcome = when {
            match.blocked -> FacePipeline.Outcome.BLOCKED
            match.matched -> FacePipeline.Outcome.MATCH
            else -> FacePipeline.Outcome.NO_MATCH
        }
        // Estimate coarse appearance only for a non-owner face (drives evidence labels and the
        // optional appearance rules). Skipped for the owner to save per-frame work.
        val look = if (outcome == FacePipeline.Outcome.NO_MATCH || outcome == FacePipeline.Outcome.BLOCKED) {
            runCatching { appearance.analyze(upright, face) }.getOrNull()?.let { a ->
                if (gender.isAvailable) a.copy(sex = runCatching { gender.classify(crop) }.getOrDefault(a.sex)) else a
            }
        } else null
        // Liveness signals from the primary face (used by the per-app blink challenge).
        val eyesOpen = ((face.leftEyeOpenProbability ?: -1f) + (face.rightEyeOpenProbability ?: -1f)).let {
            if (it < 0f) null else it / 2f
        }
        return FacePipeline.Analysis(
            outcome, match.similarity, match.personName, match.personId,
            appearance = look,
            eyesOpen = eyesOpen,
            headYaw = face.headEulerAngleY,
            headPitch = face.headEulerAngleX,
        )
    }

    private companion object {
        /** Below this mean face luminance (0..255) we won't trust a recognition result. */
        const val MIN_RECOGNITION_LUMA = 45f
        /** Below this luminance we start raising the acceptance threshold. */
        const val DIM_LUMA = 100f
        /** Max extra cosine added to the threshold at the darkest still-usable light. */
        const val DIM_THRESHOLD_BOOST = 0.08f
    }
}

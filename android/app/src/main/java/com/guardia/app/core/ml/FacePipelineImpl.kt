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
        // `rotate` returns the source itself when there is nothing to rotate, in which case the
        // bitmap belongs to the caller and must not be released here.
        val upright = BitmapUtils.rotate(bitmap, rotationDegrees)
        val ownsUpright = upright !== bitmap
        // Every intermediate this method creates — the face crop, and one aligned crop per pipeline
        // version — is released before returning. A recognition check allocated four or five
        // bitmaps per frame and left all of them for the collector; on the guard loop that is the
        // difference between a flat heap and a sawtooth with a GC pause in the middle of a check.
        val scratch = ArrayList<Bitmap>(3)
        try {
            return analyzeInner(upright, sensitivity, scratch)
        } finally {
            for (b in scratch) runCatching { if (!b.isRecycled) b.recycle() }
            if (ownsUpright) runCatching { if (!upright.isRecycled) upright.recycle() }
        }
    }

    private suspend fun analyzeInner(
        upright: Bitmap,
        sensitivity: Float,
        scratch: MutableList<Bitmap>,
    ): FacePipeline.Analysis {
        val faces = quality.detectFast(upright)
        when {
            faces.isEmpty() -> return FacePipeline.Analysis(FacePipeline.Outcome.NO_FACE, 0f, null)
            faces.size > 1 -> return FacePipeline.Analysis(FacePipeline.Outcome.MULTIPLE_FACES, 0f, null)
        }
        val face = faces[0]
        val crop = BitmapUtils.crop(upright, face.boundingBox)
            ?: return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.FACE_UNCLEAR)
        // `Bitmap.createBitmap(source, ...)` hands back the *source* when the rectangle covers the
        // whole image, so a full-frame face would otherwise put the caller's bitmap on the scratch
        // list and get it recycled out from under them.
        trackIfOwned(scratch, crop, upright)

        // In very low light, embeddings are unreliable and can falsely match another person.
        // Refuse to make a recognition decision rather than risk a false accept. (Luminance is
        // measured on the raw crop so brightness normalization can't mask a genuinely dark scene.)
        val luma = BitmapUtils.averageLuminance(crop)
        if (luma < MIN_RECOGNITION_LUMA) {
            return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.LOW_LIGHT)
        }

        // Blur is the other way a frame produces a meaningless embedding, and it is the more
        // dangerous one: a badly blurred face lands near the *centre* of the embedding space, close
        // to everybody, so it reads as a weak match against whoever happens to be enrolled. Unlike
        // darkness it isn't visible in the luminance, so it gets its own gate.
        val sharp = BitmapUtils.sharpness(crop)
        if (sharp < MIN_RECOGNITION_SHARPNESS) {
            return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.FACE_UNCLEAR)
        }

        // In dim (but usable) light, raise the bar so a noisy embedding can't false-accept a stranger.
        val dimBoost = if (luma < DIM_LUMA)
            DIM_THRESHOLD_BOOST * ((DIM_LUMA - luma) / (DIM_LUMA - MIN_RECOGNITION_LUMA)) else 0f
        // Same idea for softness: a usable-but-soft frame has to clear a higher bar than a crisp one.
        val softBoost = if (sharp < SOFT_SHARPNESS)
            SOFT_THRESHOLD_BOOST * ((SOFT_SHARPNESS - sharp) / (SOFT_SHARPNESS - MIN_RECOGNITION_SHARPNESS)) else 0f
        val thresholdBoost = dimBoost + softBoost

        // Build one probe embedding per pipeline version present in the enrollment, each through
        // that version's own preprocessing, so faces enrolled before an upgrade are compared the way
        // they were stored. See EmbeddingMath.VERSION for what each version means.
        val versions = people.usedModelVersions()
        if (versions.isEmpty()) {
            return FacePipeline.Analysis(FacePipeline.Outcome.INCONCLUSIVE, 0f, null, reason = FacePipeline.InconclusiveReason.NO_ENROLLMENT)
        }
        val probes = HashMap<Int, FloatArray>(versions.size)
        for (v in versions) {
            probes[v] = when (v) {
                EmbeddingMath.VERSION ->
                    embedder.embed(FaceAligner.align(upright, face).also { trackIfOwned(scratch, it, upright) })
                V2_EYES_ONLY_ALIGNMENT ->
                    embedder.embed(FaceAligner.alignV2(upright, face).also { trackIfOwned(scratch, it, upright) })
                else -> embedder.embedLegacy(crop)
            }
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
            margin = match.margin,
        )
    }

    /** Registers [candidate] for recycling unless it is actually [source] or already tracked. */
    private fun trackIfOwned(scratch: MutableList<Bitmap>, candidate: Bitmap, source: Bitmap) {
        if (candidate === source) return
        if (scratch.any { it === candidate }) return
        scratch += candidate
    }

    private companion object {
        /** Below this mean face luminance (0..255) we won't trust a recognition result. */
        const val MIN_RECOGNITION_LUMA = 45f
        /** Below this luminance we start raising the acceptance threshold. */
        const val DIM_LUMA = 100f
        /** Max extra cosine added to the threshold at the darkest still-usable light. */
        const val DIM_THRESHOLD_BOOST = 0.08f

        /**
         * Below this mean-absolute-Laplacian response the crop is too blurred to identify anyone.
         * Calibrated against [BitmapUtils.sharpness]'s fixed 48x48 measurement grid: a face in
         * normal use scores well into double digits, a hand-shake blur drops it to low single
         * digits, and a completely defocused frame approaches zero.
         */
        const val MIN_RECOGNITION_SHARPNESS = 3.5f
        /** Below this sharpness we start raising the acceptance threshold. */
        const val SOFT_SHARPNESS = 9f
        /** Max extra cosine added to the threshold at the softest still-usable frame. */
        const val SOFT_THRESHOLD_BOOST = 0.06f

        /** Pipeline version whose probes need the eyes-only alignment ([FaceAligner.alignV2]). */
        const val V2_EYES_ONLY_ALIGNMENT = 2
    }
}

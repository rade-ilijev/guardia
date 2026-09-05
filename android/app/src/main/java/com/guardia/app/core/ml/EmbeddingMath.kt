package com.guardia.app.core.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Helpers for serializing and comparing face embeddings. */
object EmbeddingMath {

    /**
     * Version of the embedding pipeline (preprocessing + model usage). Bump whenever the way crops are
     * aligned/normalized or embedded changes, so stored embeddings from an older pipeline are no
     * longer compared against new probes (a mismatch could otherwise lock the owner out).
     *
     * Rather than discarding older samples, [com.guardia.app.core.ml.FacePipelineImpl] builds one
     * probe *per version present in the enrollment*, each through that version's own preprocessing:
     *
     *  - 3 — two-axis alignment ([FaceAligner.align]) + brightness normalization + flip averaging
     *  - 2 — eyes-only alignment ([FaceAligner.alignV2]) + brightness normalization + flip averaging
     *  - 0/1 — plain bounding-box crop, fixed [-1,1] normalization, single pass
     *
     * so an app update never invalidates a face the user already enrolled.
     */
    const val VERSION = 3

    fun toBytes(vec: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vec.forEach { buf.putFloat(it) }
        return buf.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buf.float
        return out
    }

    /**
     * Quality-weighted, L2-normalized mean of several embeddings (a person's "prototype" face).
     *
     * Weighting matters more than it looks: an enrollment set almost always contains one or two
     * frames that were slightly blurred or badly lit, and an unweighted mean lets those drag the
     * prototype away from the person. [weights] are clamped to a floor so a low-scoring sample is
     * de-emphasised rather than discarded — it still carries pose information the good frames lack.
     */
    fun weightedCentroid(vectors: List<FloatArray>, weights: FloatArray): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val dim = vectors[0].size
        val sum = FloatArray(dim)
        var total = 0f
        for (i in vectors.indices) {
            val v = vectors[i]
            if (v.size != dim) continue
            val w = (weights.getOrElse(i) { 1f }).coerceIn(MIN_SAMPLE_WEIGHT, 1f)
            for (j in 0 until dim) sum[j] += v[j] * w
            total += w
        }
        if (total <= 0f) return l2Normalize(sum)
        for (j in 0 until dim) sum[j] /= total
        return l2Normalize(sum)
    }

    /**
     * Mean of the [k] highest values in [sims], or of all of them when there are fewer than [k].
     *
     * Used instead of a plain `max` when scoring a probe against a person's samples. A single best
     * sample is a noisy statistic — one lucky frame can carry a look-alike over the line — while a
     * mean over every sample is dragged down by the off-angle poses that were enrolled on purpose.
     * The mean of the best few is stable against both.
     */
    fun topKMean(sims: FloatArray, k: Int): Float {
        if (sims.isEmpty()) return -1f
        val n = minOf(k, sims.size)
        if (n == sims.size) return sims.average().toFloat()
        val copy = sims.copyOf()
        copy.sort()               // ascending
        var sum = 0f
        for (i in copy.size - n until copy.size) sum += copy[i]
        return sum / n
    }

    /**
     * Dot product — cosine similarity for vectors that are already L2-normalized, which every
     * embedding in this app is (both stored samples and freshly-computed probes).
     *
     * [cosine] recomputes both magnitudes on every call: three multiply-accumulate loops and two
     * square roots per comparison. The guard loop runs this against every enrolled sample on every
     * frame, so skipping the redundant work is the single cheapest speed-up in the pipeline.
     */
    fun dot(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    /** Floor applied to a sample's quality weight, so a weak frame is down-weighted, never dropped. */
    const val MIN_SAMPLE_WEIGHT = 0.35f

    /** L2-normalized mean of several embeddings (a person's "prototype" face). */
    fun centroid(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val dim = vectors[0].size
        val sum = FloatArray(dim)
        for (v in vectors) {
            if (v.size != dim) continue
            for (i in 0 until dim) sum[i] += v[i]
        }
        for (i in 0 until dim) sum[i] /= vectors.size
        return l2Normalize(sum)
    }

    fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) sum += v * v
        val norm = sqrt(sum).coerceAtLeast(1e-10f)
        return FloatArray(vec.size) { vec[it] / norm }
    }

    /** Cosine similarity in [-1, 1]; inputs need not be normalized. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = (sqrt(na) * sqrt(nb)).coerceAtLeast(1e-10f)
        return dot / denom
    }
}

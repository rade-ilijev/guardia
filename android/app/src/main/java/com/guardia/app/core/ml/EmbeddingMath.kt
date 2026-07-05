package com.guardia.app.core.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Helpers for serializing and comparing face embeddings. */
object EmbeddingMath {

    /**
     * Version of the embedding pipeline (preprocessing + model usage). Bump whenever the way crops are
     * aligned/normalized or embedded changes, so stored embeddings from an older pipeline are no
     * longer compared against new probes (a mismatch could otherwise lock the owner out). Samples from
     * an older version are ignored by the recognizer, and the UI prompts the user to re-enroll.
     */
    const val VERSION = 2

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

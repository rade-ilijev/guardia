package com.guardia.app

import com.guardia.app.core.ml.EmbeddingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EmbeddingMathTest {

    @Test
    fun toBytes_fromBytes_roundTrips() {
        val vec = floatArrayOf(0.1f, -0.5f, 2.0f, 123.456f, 0f)
        val restored = EmbeddingMath.fromBytes(EmbeddingMath.toBytes(vec))
        assertEquals(vec.size, restored.size)
        for (i in vec.indices) assertEquals(vec[i], restored[i], 1e-6f)
    }

    @Test
    fun l2Normalize_producesUnitVector() {
        val norm = EmbeddingMath.l2Normalize(floatArrayOf(3f, 4f))
        val magnitude = sqrt(norm[0] * norm[0] + norm[1] * norm[1])
        assertEquals(1f, magnitude, 1e-5f)
    }

    @Test
    fun l2Normalize_handlesZeroVectorWithoutNaN() {
        val norm = EmbeddingMath.l2Normalize(floatArrayOf(0f, 0f, 0f))
        norm.forEach { assertTrue(!it.isNaN()) }
    }

    @Test
    fun cosine_identicalVectorsIsOne() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, EmbeddingMath.cosine(v, v), 1e-5f)
    }

    @Test
    fun cosine_orthogonalVectorsIsZero() {
        assertEquals(0f, EmbeddingMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-5f)
    }

    @Test
    fun cosine_oppositeVectorsIsMinusOne() {
        assertEquals(-1f, EmbeddingMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-5f)
    }

    @Test
    fun cosine_mismatchedSizesReturnsMinusOne() {
        assertEquals(-1f, EmbeddingMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0f)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers added for quality-weighted, top-K recognition scoring.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun dot_matchesCosineForNormalizedVectors() {
        val a = EmbeddingMath.l2Normalize(floatArrayOf(1f, 2f, 3f))
        val b = EmbeddingMath.l2Normalize(floatArrayOf(-1f, 4f, 0.5f))
        assertEquals(EmbeddingMath.cosine(a, b), EmbeddingMath.dot(a, b), 1e-5f)
    }

    @Test
    fun dot_rejectsMismatchedDimensions() {
        assertEquals(-1f, EmbeddingMath.dot(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f)), 1e-6f)
    }

    @Test
    fun topKMean_averagesTheBestK() {
        val sims = floatArrayOf(0.1f, 0.9f, 0.5f, 0.8f, 0.2f)
        // best three are 0.9, 0.8, 0.5
        assertEquals((0.9f + 0.8f + 0.5f) / 3f, EmbeddingMath.topKMean(sims, 3), 1e-5f)
    }

    @Test
    fun topKMean_averagesEverythingWhenKExceedsSize() {
        val sims = floatArrayOf(0.2f, 0.4f)
        assertEquals(0.3f, EmbeddingMath.topKMean(sims, 5), 1e-5f)
    }

    @Test
    fun topKMean_handlesEmptyInput() {
        assertEquals(-1f, EmbeddingMath.topKMean(FloatArray(0), 3), 1e-6f)
    }

    @Test
    fun weightedCentroid_leansTowardTheHigherQualitySample() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val even = EmbeddingMath.weightedCentroid(listOf(a, b), floatArrayOf(1f, 1f))
        val leanA = EmbeddingMath.weightedCentroid(listOf(a, b), floatArrayOf(1f, 0.1f))
        assertEquals(even[0], even[1], 1e-5f)
        assertTrue("a high-quality sample should pull the prototype toward it", leanA[0] > even[0])
    }

    @Test
    fun weightedCentroid_isUnitLength() {
        val c = EmbeddingMath.weightedCentroid(
            listOf(floatArrayOf(3f, 0f), floatArrayOf(0f, 4f)),
            floatArrayOf(0.8f, 0.2f),
        )
        assertEquals(1f, sqrt(c[0] * c[0] + c[1] * c[1]), 1e-5f)
    }

    @Test
    fun weightedCentroid_floorsWeightsSoAWeakSampleStillCounts() {
        // A zero weight is clamped to MIN_SAMPLE_WEIGHT, so a poor capture is de-emphasised rather
        // than discarded — it still carries pose information the good frames lack.
        val c = EmbeddingMath.weightedCentroid(
            listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            floatArrayOf(1f, 0f),
        )
        assertTrue(c[1] > 0f)
    }

    @Test
    fun weightedCentroid_handlesEmptyInput() {
        assertEquals(0, EmbeddingMath.weightedCentroid(emptyList(), FloatArray(0)).size)
    }
}

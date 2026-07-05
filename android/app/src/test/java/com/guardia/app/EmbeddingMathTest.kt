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
}

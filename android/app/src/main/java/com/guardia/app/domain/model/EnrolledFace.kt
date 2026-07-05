package com.guardia.app.domain.model

/** A single enrolled embedding tied to a person, used by the recognizer. */
data class EnrolledFace(
    val personId: String,
    val name: String,
    val embedding: FloatArray,
    /** True if this face belongs to a block-listed (known unauthorized) person. */
    val blocked: Boolean = false,
    /** Embedding pipeline version this sample was produced with (see EmbeddingMath.VERSION). */
    val modelVersion: Int = 0,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is EnrolledFace && other.personId == personId && other.embedding.contentEquals(embedding))

    override fun hashCode(): Int = 31 * personId.hashCode() + embedding.contentHashCode()
}

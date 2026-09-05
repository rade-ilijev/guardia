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
    /**
     * How good the capture this embedding came from was (0..1), as scored at enrollment time.
     * The recognizer weights samples by it, so a slightly blurred or badly-lit enrollment frame
     * still contributes pose information without dragging the person's prototype off-centre.
     */
    val quality: Float = 1f,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is EnrolledFace && other.personId == personId && other.embedding.contentEquals(embedding))

    override fun hashCode(): Int = 31 * personId.hashCode() + embedding.contentHashCode()
}

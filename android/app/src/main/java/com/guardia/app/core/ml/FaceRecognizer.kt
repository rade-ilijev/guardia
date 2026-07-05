package com.guardia.app.core.ml

import com.guardia.app.data.PeopleRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Nearest-neighbor matcher over enrolled embeddings. */
@Singleton
class FaceRecognizer @Inject constructor(
    private val people: PeopleRepository,
) {
    data class Match(
        val matched: Boolean,
        val similarity: Float,
        val personName: String?,
        val personId: String?,
        /** True when the probe matches a known *unauthorized* (block-listed) person. */
        val blocked: Boolean = false,
        /**
         * True when there are no enabled authorized (owner) faces to compare against, so we can't
         * authenticate anyone. Callers must treat this as "can't decide" (never an intruder) to
         * avoid locking the owner out when enrollment is empty or every owner is disabled.
         */
        val noEnrolledOwners: Boolean = false,
    )

    /** Maps the user's 0..1 sensitivity setting onto a cosine threshold. */
    fun thresholdFor(sensitivity: Float): Float = 0.35f + sensitivity.coerceIn(0f, 1f) * 0.45f

    /**
     * Identifies the probe against enrolled people. The caller supplies one probe embedding per
     * pipeline version present in the enrollment ([probesByVersion]); each enrolled sample is compared
     * against the probe produced by the *same* pipeline, so faces enrolled before the pipeline was
     * upgraded still match correctly (and a preprocessing change never locks the owner out).
     *
     * Per person (and per version) we blend the *best* matching sample with the similarity to that
     * version's centroid; the blend resists a single lucky/unlucky sample, so a look-alike that matches
     * one enrollment frame won't sail through. A person's final score is the max across versions.
     *
     * [thresholdBoost] raises the acceptance bar (used in dim light, where embeddings are noisier and
     * false accepts are more likely). A block-listed person within [BLOCKED_MARGIN] of the best owner
     * still wins, biasing toward locking when a known look-alike is present.
     */
    suspend fun identify(probesByVersion: Map<Int, FloatArray>, sensitivity: Float, thresholdBoost: Float = 0f): Match {
        val enrolled = people.enrolledFaces()
        if (enrolled.isEmpty()) return Match(false, 0f, null, null, noEnrolledOwners = true)

        val bestAuth = HashMap<String, Float>()
        val bestBlk = HashMap<String, Float>()
        val nameOf = HashMap<String, String>()
        var hasAuthorized = false

        // Group by person AND version so embeddings are only ever compared with a like-version probe.
        for ((key, faces) in enrolled.groupBy { it.personId to it.modelVersion }) {
            val (personId, version) = key
            val probe = probesByVersion[version] ?: continue
            val embeddings = faces.map { it.embedding }.filter { it.size == probe.size }
            if (embeddings.isEmpty()) continue
            val blocked = faces.first().blocked
            nameOf[personId] = faces.first().name
            val bestSample = embeddings.maxOf { EmbeddingMath.cosine(probe, it) }
            val centroidSim = if (embeddings.size > 1)
                EmbeddingMath.cosine(probe, EmbeddingMath.centroid(embeddings)) else bestSample
            val score = 0.5f * bestSample + 0.5f * centroidSim
            if (blocked) {
                bestBlk[personId] = maxOf(bestBlk[personId] ?: -1f, score)
            } else {
                hasAuthorized = true
                bestAuth[personId] = maxOf(bestAuth[personId] ?: -1f, score)
            }
        }

        val authTop = bestAuth.maxByOrNull { it.value }
        val blkTop = bestBlk.maxByOrNull { it.value }
        val bestAuthSim = authTop?.value ?: -1f
        val bestBlkSim = blkTop?.value ?: -1f

        val threshold = (thresholdFor(sensitivity) + thresholdBoost).coerceIn(0f, 0.98f)
        // A block-listed match takes precedence when it clears the threshold and is close to or above
        // the best authorized match — this catches look-alikes (e.g. siblings).
        if (blkTop != null && bestBlkSim >= threshold && bestBlkSim >= bestAuthSim - BLOCKED_MARGIN) {
            return Match(matched = false, similarity = bestBlkSim, personName = nameOf[blkTop.key], personId = blkTop.key, blocked = true)
        }
        var matched = bestAuthSim >= threshold
        // Reject if the probe looks more like a face the user explicitly declined ("not me") than
        // like the owner — this sharpens recognition against look-alikes seen during gallery review.
        if (matched) {
            val bestNeg = people.negativeEmbeddings().mapNotNull { neg ->
                val probe = probesByVersion[neg.modelVersion] ?: return@mapNotNull null
                if (neg.embedding.size != probe.size) null else EmbeddingMath.cosine(probe, neg.embedding)
            }.maxOrNull() ?: -1f
            if (bestNeg > bestAuthSim) matched = false
        }
        return Match(
            matched = matched,
            similarity = bestAuthSim.coerceAtLeast(0f),
            personName = authTop?.key?.let { nameOf[it] }.takeIf { matched },
            personId = authTop?.key.takeIf { matched },
            noEnrolledOwners = !hasAuthorized,
        )
    }

    private companion object {
        /** A blocked look-alike within this cosine margin of the best owner still triggers a lock. */
        const val BLOCKED_MARGIN = 0.05f
    }
}

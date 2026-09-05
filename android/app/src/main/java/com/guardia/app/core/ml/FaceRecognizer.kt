package com.guardia.app.core.ml

import com.guardia.app.data.PeopleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nearest-neighbour matcher over enrolled embeddings.
 *
 * Recognition here is not "is this face similar enough to the owner" but "is this face *more*
 * like the owner than like anyone else we know about". Those are different questions, and only the
 * second one is safe: a fixed similarity threshold cannot distinguish a genuine owner from a
 * sibling, because siblings really are that similar. So every decision below is made against two
 * numbers — the owner's score, and the best score from the impostor cohort (block-listed people
 * plus faces the user has explicitly declined) — and a match needs to win by a margin.
 */
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
        /**
         * How far the winning owner score cleared the best impostor score, or 1 when no
         * block-listed or declined face was comparable at all (an uncontested match). Near zero
         * means "someone who looks a lot like them was also in the running". Zero on a non-match.
         * Surfaced so the UI and the guard loop can treat a narrow win more cautiously.
         */
        val margin: Float = 0f,
    )

    /** Maps the user's 0..1 sensitivity setting onto a cosine threshold. */
    fun thresholdFor(sensitivity: Float): Float = 0.35f + sensitivity.coerceIn(0f, 1f) * 0.45f

    /**
     * Identifies the probe against enrolled people. The caller supplies one probe embedding per
     * pipeline version present in the enrollment ([probesByVersion]); each enrolled sample is compared
     * against the probe produced by the *same* pipeline, so faces enrolled before the pipeline was
     * upgraded still match correctly (and a preprocessing change never locks the owner out).
     *
     * Scoring, per person and per version:
     *
     *  1. Cosine against every stored sample of theirs. Because both sides are L2-normalized this is
     *     a plain dot product ([EmbeddingMath.dot]).
     *  2. The mean of the best [TOP_K] of those, rather than the single best. One lucky frame should
     *     not be able to carry a look-alike over the line, and one unlucky frame should not sink a
     *     genuine owner.
     *  3. Blended 50/50 with the similarity to that version's quality-weighted centroid, which is a
     *     more stable summary of the person than any individual capture.
     *
     * A person's final score is the best they achieve across versions.
     *
     * [thresholdBoost] raises the acceptance bar (used in dim light and on blurred frames, where
     * embeddings are noisier and false accepts are more likely). A block-listed person within
     * [BLOCKED_MARGIN] of the best owner still wins, biasing toward locking when a known look-alike
     * is present.
     */
    suspend fun identify(probesByVersion: Map<Int, FloatArray>, sensitivity: Float, thresholdBoost: Float = 0f): Match {
        val prototypes = people.prototypes()
        if (prototypes.isEmpty()) return Match(false, 0f, null, null, noEnrolledOwners = true)

        val bestAuth = HashMap<String, Float>()
        val bestBlk = HashMap<String, Float>()
        val nameOf = HashMap<String, String>()
        var hasAuthorized = false

        for (proto in prototypes) {
            val probe = probesByVersion[proto.modelVersion] ?: continue
            val score = scoreAgainst(proto, probe)
            if (score <= NO_SCORE) continue
            nameOf[proto.personId] = proto.name
            if (proto.blocked) {
                bestBlk[proto.personId] = maxOf(bestBlk[proto.personId] ?: NO_SCORE, score)
            } else {
                hasAuthorized = true
                bestAuth[proto.personId] = maxOf(bestAuth[proto.personId] ?: NO_SCORE, score)
            }
        }

        val authTop = bestAuth.maxByOrNull { it.value }
        val blkTop = bestBlk.maxByOrNull { it.value }
        val bestAuthSim = authTop?.value ?: NO_SCORE
        val bestBlkSim = blkTop?.value ?: NO_SCORE

        val threshold = (thresholdFor(sensitivity) + thresholdBoost).coerceIn(0f, 0.98f)

        // A block-listed match takes precedence when it clears the threshold and is close to or above
        // the best authorized match — this catches look-alikes (e.g. siblings).
        if (blkTop != null && bestBlkSim >= threshold && bestBlkSim >= bestAuthSim - BLOCKED_MARGIN) {
            return Match(
                matched = false, similarity = bestBlkSim,
                personName = nameOf[blkTop.key], personId = blkTop.key, blocked = true,
            )
        }

        var matched = bestAuthSim >= threshold
        var margin = 0f

        if (matched) {
            // The impostor cohort: every face we know is *not* an owner. Block-listed people count
            // even when they didn't clear the threshold above, and so does anything the user
            // declined during gallery review ("that isn't me") — those declines are the strongest
            // negative evidence the app ever gets, because a human looked at the face and said so.
            val bestNeg = bestNegative(probesByVersion)
            val bestImpostor = maxOf(bestBlkSim, bestNeg)
            // With nobody to compare against, "margin" has no meaning; report a full 1 rather than
            // an arbitrary number derived from the NO_SCORE sentinel.
            margin = if (bestImpostor > NO_SCORE) bestAuthSim - bestImpostor else 1f

            // Sensitivity already sets how similar a face must be; it also sets how *decisively* the
            // owner has to win. At the default setting this is a 2% cosine margin — enough to reject
            // a probe that is essentially tied with a known look-alike, without second-guessing the
            // ordinary case where the owner wins by a mile.
            val requiredMargin = MAX_IMPOSTOR_MARGIN * sensitivity.coerceIn(0f, 1f)
            if (bestImpostor > NO_SCORE && margin < requiredMargin) matched = false
        }

        return Match(
            matched = matched,
            similarity = bestAuthSim.coerceAtLeast(0f),
            personName = authTop?.key?.let { nameOf[it] }.takeIf { matched },
            personId = authTop?.key.takeIf { matched },
            noEnrolledOwners = !hasAuthorized,
            margin = if (matched) margin else 0f,
        )
    }

    /** Best similarity between the probe and any face the user explicitly declined. */
    private suspend fun bestNegative(probesByVersion: Map<Int, FloatArray>): Float {
        var best = NO_SCORE
        for (neg in people.negativeEmbeddings()) {
            val probe = probesByVersion[neg.modelVersion] ?: continue
            if (neg.embedding.size != probe.size) continue
            val sim = EmbeddingMath.dot(probe, neg.embedding)
            if (sim > best) best = sim
        }
        return best
    }

    /**
     * Score for one person's samples at one pipeline version. Returns [NO_SCORE] when nothing in the
     * prototype has the probe's dimensionality (a different model was bundled since enrollment).
     */
    private fun scoreAgainst(proto: PeopleRepository.PersonPrototype, probe: FloatArray): Float {
        var n = 0
        val sims = FloatArray(proto.embeddings.size)
        for (i in proto.embeddings.indices) {
            val e = proto.embeddings[i]
            if (e.size != probe.size) continue
            // Quality nudges a sample's own similarity as well as its share of the centroid, so a
            // weak capture cannot single-handedly produce the top-K score either.
            val w = proto.weights.getOrElse(i) { 1f }.coerceIn(EmbeddingMath.MIN_SAMPLE_WEIGHT, 1f)
            sims[n++] = EmbeddingMath.dot(probe, e) * (QUALITY_FLOOR + (1f - QUALITY_FLOOR) * w)
        }
        if (n == 0) return NO_SCORE
        val sampleScore = EmbeddingMath.topKMean(if (n == sims.size) sims else sims.copyOf(n), TOP_K)
        val centroid = proto.centroid
        val centroidScore =
            if (n > 1 && centroid.size == probe.size) EmbeddingMath.dot(probe, centroid) else sampleScore
        return SAMPLE_BLEND * sampleScore + (1f - SAMPLE_BLEND) * centroidScore
    }

    private companion object {
        /** A blocked look-alike within this cosine margin of the best owner still triggers a lock. */
        const val BLOCKED_MARGIN = 0.05f

        /**
         * Margin by which the owner must beat the best known impostor, at maximum sensitivity.
         * Scaled by the user's sensitivity setting, so a user who has loosened recognition also
         * loosens this. Deliberately small: it exists to break near-ties, not to add a second
         * threshold that could lock the owner out.
         */
        const val MAX_IMPOSTOR_MARGIN = 0.04f

        /** How many of a person's best-matching samples are averaged into the sample score. */
        const val TOP_K = 3

        /** Weight of the top-K sample score against the centroid score. */
        const val SAMPLE_BLEND = 0.5f

        /**
         * The share of a sample's similarity that survives regardless of its quality weight. A
         * bottom-quality sample keeps 90% of its score; the weighting is a tiebreaker between
         * captures, not a way to silence one.
         */
        const val QUALITY_FLOOR = 0.9f

        /** Sentinel for "no comparable sample" — below any real cosine similarity. */
        const val NO_SCORE = -1f
    }
}

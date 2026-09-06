package com.guardia.app.data

import com.guardia.app.core.ml.EmbeddingMath
import com.guardia.app.data.db.FaceSampleEntity
import com.guardia.app.data.db.NegativeFaceEntity
import com.guardia.app.data.db.PersonDao
import com.guardia.app.data.db.PersonEntity
import com.guardia.app.domain.model.EnrolledFace
import com.guardia.app.domain.model.FaceSample
import com.guardia.app.domain.model.Person
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class PeopleRepository @Inject constructor(
    private val dao: PersonDao,
) {
    // Cached snapshots so the per-frame guard loop doesn't re-read and re-parse the DB every frame.
    // Invalidated on any write that changes which embeddings/labels the recognizer should use.
    @Volatile private var enrolledCache: List<EnrolledFace>? = null
    @Volatile private var negativesCache: List<VersionedEmbedding>? = null
    @Volatile private var prototypeCache: List<PersonPrototype>? = null
    @Volatile private var versionsCache: Set<Int>? = null

    /** An embedding tagged with the pipeline version that produced it. */
    data class VersionedEmbedding(val embedding: FloatArray, val modelVersion: Int)

    private fun invalidateCaches() {
        enrolledCache = null
        negativesCache = null
        prototypeCache = null
        versionsCache = null
    }

    /**
     * One person's enrolled faces for a single pipeline version, pre-grouped and with the
     * quality-weighted centroid already computed.
     *
     * The recognizer used to do this grouping and this centroid arithmetic *on every camera frame*:
     * a `groupBy` over every sample in the database, then a fresh mean vector per person, several
     * times a minute for as long as guarding is on. None of it changes between frames — it only
     * changes when the enrollment does — so it is computed here instead and thrown away by
     * [invalidateCaches] on any write.
     */
    data class PersonPrototype(
        val personId: String,
        val name: String,
        val blocked: Boolean,
        val modelVersion: Int,
        val embeddings: List<FloatArray>,
        /** Per-sample quality weights, parallel to [embeddings]. */
        val weights: FloatArray,
        /** Quality-weighted, L2-normalized mean of [embeddings]. */
        val centroid: FloatArray,
    )

    /** Public cache-drop for writers that bypass this repository (e.g. backup restore via the DAO). */
    fun invalidate() = invalidateCaches()

    /** True when there are enrolled samples but none from the current pipeline (re-enroll needed). */
    val needsReenroll: Flow<Boolean> = combine(
        dao.observeTotalSampleCount(),
        dao.observeUsableSampleCount(EmbeddingMath.VERSION),
    ) { total, usable -> total > 0 && usable == 0 }
    val people: Flow<List<Person>> = dao.observePeople().map { rows ->
        rows.map { it.toPerson() }
    }

    private fun com.guardia.app.data.db.PersonWithCount.toPerson(): Person = Person(
        id = person.id,
        name = person.name,
        sampleCount = sampleCount,
        photoPath = person.photoPath,
        createdAt = person.createdAt,
        lastSeenAt = person.lastSeenAt,
        recognitionCount = person.recognitionCount,
        avgConfidence = if (person.recognitionCount > 0)
            (person.confidenceSum / person.recognitionCount).toFloat() else 0f,
        enabled = person.enabled,
        blocked = person.blocked,
        gender = person.gender,
        expiresAt = person.expiresAt,
    )

    /** Creates a person with optional face embeddings. Returns the new id. */
    suspend fun addPerson(
        name: String,
        photoPath: String?,
        embeddings: List<FloatArray> = emptyList(),
        blocked: Boolean = false,
        gender: String? = null,
        expiresAt: Long? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.insertPerson(
            PersonEntity(
                id = id,
                name = name.ifBlank { if (blocked) "Unknown person" else "Unnamed" },
                photoPath = photoPath,
                createdAt = System.currentTimeMillis(),
                blocked = blocked,
                gender = gender,
                expiresAt = expiresAt,
            )
        )
        if (embeddings.isNotEmpty()) addSamples(id, embeddings)
        invalidateCaches()
        return id
    }

    /** Deletes guest passes whose time is up; returns how many were removed. */
    suspend fun purgeExpiredGuests(now: Long = System.currentTimeMillis()): Int {
        val expired = dao.allPeople().filter { it.expiresAt != null && it.expiresAt <= now }
        expired.forEach { remove(it.id) }
        return expired.size
    }

    suspend fun setGender(id: String, gender: String?) {
        dao.setGender(id, gender)
        invalidateCaches()
    }

    suspend fun addSamples(personId: String, embeddings: List<FloatArray>, quality: Float = 1f) {
        val now = System.currentTimeMillis()
        dao.insertSamples(
            embeddings.map { vec ->
                FaceSampleEntity(
                    id = UUID.randomUUID().toString(),
                    personId = personId,
                    embedding = EmbeddingMath.toBytes(EmbeddingMath.l2Normalize(vec)),
                    quality = quality,
                    createdAt = now,
                    modelVersion = EmbeddingMath.VERSION,
                )
            }
        )
        invalidateCaches()
    }

    /** Adds a single sample (optionally with the cropped image it came from). */
    suspend fun addSample(personId: String, embedding: FloatArray, photoPath: String?, quality: Float = 1f) {
        dao.insertSamples(
            listOf(
                FaceSampleEntity(
                    id = UUID.randomUUID().toString(),
                    personId = personId,
                    embedding = EmbeddingMath.toBytes(EmbeddingMath.l2Normalize(embedding)),
                    quality = quality,
                    createdAt = System.currentTimeMillis(),
                    photoPath = photoPath,
                    modelVersion = EmbeddingMath.VERSION,
                )
            )
        )
        invalidateCaches()
    }

    fun person(id: String): Flow<Person?> = dao.observePerson(id).map { row -> row?.toPerson() }

    /** All enrolled samples for a person (newest first), for showing what the model trained on. */
    fun samples(personId: String): Flow<List<FaceSample>> = dao.observeSamples(personId).map { rows ->
        rows.map { FaceSample(it.id, it.photoPath, it.createdAt) }
    }

    suspend fun deleteSample(id: String) {
        dao.deleteSample(id)
        invalidateCaches()
    }

    /** A person's enrolled embeddings, used to score how well a new face matches them. */
    suspend fun personEmbeddings(personId: String): List<FloatArray> =
        dao.samplesFor(personId).map { EmbeddingMath.fromBytes(it.embedding) }

    /** Records a face the user said is NOT [personId] (or globally, when null). */
    suspend fun addNegative(personId: String?, embedding: FloatArray, photoPath: String?) {
        dao.insertNegatives(
            listOf(
                NegativeFaceEntity(
                    id = UUID.randomUUID().toString(),
                    personId = personId,
                    embedding = EmbeddingMath.toBytes(EmbeddingMath.l2Normalize(embedding)),
                    photoPath = photoPath,
                    createdAt = System.currentTimeMillis(),
                    modelVersion = EmbeddingMath.VERSION,
                )
            )
        )
        invalidateCaches()
    }

    /** Negative (known not-the-owner) embeddings used by the recognizer to reject look-alikes. */
    suspend fun negativeEmbeddings(): List<VersionedEmbedding> {
        negativesCache?.let { return it }
        return dao.allNegatives()
            .map { VersionedEmbedding(EmbeddingMath.fromBytes(it.embedding), it.modelVersion) }
            .also { negativesCache = it }
    }

    /**
     * Distinct embedding-pipeline versions present across enrolled faces and negatives.
     *
     * Cached with the lists it is derived from. The guard pipeline asks for this on every frame,
     * and computing it boxed every element of both lists into two new lists, concatenated them,
     * and built a set — for a value that only changes when someone is enrolled or removed.
     */
    suspend fun usedModelVersions(): Set<Int> {
        versionsCache?.let { return it }
        val versions = HashSet<Int>()
        for (f in enrolledFaces()) versions.add(f.modelVersion)
        for (n in negativeEmbeddings()) versions.add(n.modelVersion)
        return versions.also { versionsCache = it }
    }

    /** Adds a face to the shared block list bucket so it always triggers a lock. */
    suspend fun addToBlacklist(embedding: FloatArray, photoPath: String?) {
        val existing = dao.allPeople().firstOrNull { it.blocked && it.name == BLACKLIST_BUCKET }
        val id = existing?.id ?: addPerson(name = BLACKLIST_BUCKET, photoPath = photoPath, blocked = true)
        addSample(id, embedding, photoPath)
    }

    suspend fun rename(id: String, name: String) {
        if (name.isNotBlank()) {
            dao.updateName(id, name)
            invalidateCaches()
        }
    }

    /** Records a successful recognition of [personId] with the given match [similarity] (0..1). */
    suspend fun recordRecognition(personId: String, similarity: Float) {
        dao.recordRecognition(personId, System.currentTimeMillis(), similarity.toDouble())
    }

    suspend fun setEnabled(personId: String, enabled: Boolean) {
        dao.setEnabled(personId, enabled)
        invalidateCaches()
    }

    /** Moves a person between the allowed and block lists. */
    suspend fun setBlocked(personId: String, blocked: Boolean) {
        dao.setBlocked(personId, blocked)
        invalidateCaches()
    }

    suspend fun remove(id: String) {
        dao.deleteSamples(id)
        dao.deleteNegativesFor(id)
        dao.deletePerson(id)
        invalidateCaches()
    }

    /**
     * Embeddings used by the recognizer: enabled authorized people plus *all* block-listed people
     * (each tagged via [EnrolledFace.blocked]). Disabled authorized people are skipped.
     */
    suspend fun enrolledFaces(): List<EnrolledFace> {
        enrolledCache?.let { return it }
        val now = System.currentTimeMillis()
        val byId = dao.allPeople().associateBy { it.id }
        return dao.allSamples()
            .mapNotNull { sample ->
                val p = byId[sample.personId] ?: return@mapNotNull null
                if (!p.blocked && !p.enabled) return@mapNotNull null
                // An expired guest pass no longer authorizes anyone (the purge deletes them soon
                // after, but never trust an expired face even before that happens).
                if (!p.blocked && p.expiresAt != null && p.expiresAt <= now) return@mapNotNull null
                EnrolledFace(
                    p.id, p.name, EmbeddingMath.fromBytes(sample.embedding),
                    blocked = p.blocked, modelVersion = sample.modelVersion,
                    quality = sample.quality,
                )
            }
            .also { enrolledCache = it }
    }

    /**
     * Enrolled faces grouped by (person, pipeline version), with centroids precomputed. Cached for
     * the same lifetime as [enrolledFaces]; see [PersonPrototype] for why this lives here.
     */
    suspend fun prototypes(): List<PersonPrototype> {
        prototypeCache?.let { return it }
        val built = enrolledFaces()
            .groupBy { it.personId to it.modelVersion }
            .map { (key, faces) ->
                val (personId, version) = key
                val embeddings = faces.map { it.embedding }
                val weights = FloatArray(faces.size) { faces[it].quality }
                PersonPrototype(
                    personId = personId,
                    name = faces.first().name,
                    blocked = faces.first().blocked,
                    modelVersion = version,
                    embeddings = embeddings,
                    weights = weights,
                    centroid = if (embeddings.size > 1) EmbeddingMath.weightedCentroid(embeddings, weights)
                    else embeddings.firstOrNull() ?: FloatArray(0),
                )
            }
        prototypeCache = built
        return built
    }

    /**
     * All face-image paths still referenced by a sample, negative, or person avatar. Used to find and
     * delete orphaned crops in the faces directory. (Intruder photos live elsewhere, encrypted.)
     */
    suspend fun referencedPhotoPaths(): Set<String> = buildSet {
        dao.allSamples().forEach { it.photoPath?.let(::add) }
        dao.allNegatives().forEach { it.photoPath?.let(::add) }
        dao.allPeople().forEach { it.photoPath?.let(::add) }
    }

    private companion object {
        const val BLACKLIST_BUCKET = "Blacklisted faces"
    }
}

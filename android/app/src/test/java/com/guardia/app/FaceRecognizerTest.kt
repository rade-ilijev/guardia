package com.guardia.app

import com.guardia.app.core.ml.EmbeddingMath
import com.guardia.app.core.ml.FaceRecognizer
import com.guardia.app.data.PeopleRepository
import com.guardia.app.data.db.FaceSampleEntity
import com.guardia.app.data.db.NegativeFaceEntity
import com.guardia.app.data.db.PersonDao
import com.guardia.app.data.db.PersonEntity
import com.guardia.app.data.db.PersonWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceRecognizerTest {

    /** In-memory PersonDao for host-side tests. */
    private class FakeDao(
        private val people: MutableList<PersonEntity> = mutableListOf(),
        private val samples: MutableList<FaceSampleEntity> = mutableListOf(),
        private val negatives: MutableList<NegativeFaceEntity> = mutableListOf(),
    ) : PersonDao {
        override fun observePeople(): Flow<List<PersonWithCount>> =
            flowOf(people.map { PersonWithCount(it, samples.count { s -> s.personId == it.id }) })
        override suspend fun insertPerson(person: PersonEntity) { people.add(person) }
        override suspend fun insertSamples(samplesIn: List<FaceSampleEntity>) { samples.addAll(samplesIn) }
        override suspend fun allSamples(): List<FaceSampleEntity> = samples
        override fun observeSamples(personId: String): Flow<List<FaceSampleEntity>> =
            flowOf(samples.filter { it.personId == personId })
        override suspend fun samplesFor(personId: String): List<FaceSampleEntity> =
            samples.filter { it.personId == personId }
        override suspend fun deleteSample(id: String) { samples.removeAll { it.id == id } }
        override suspend fun insertNegatives(negativesIn: List<NegativeFaceEntity>) { negatives.addAll(negativesIn) }
        override suspend fun allNegatives(): List<NegativeFaceEntity> = negatives
        override fun observeTotalSampleCount(): Flow<Int> = flowOf(samples.size)
        override fun observeUsableSampleCount(version: Int): Flow<Int> =
            flowOf(samples.count { it.modelVersion == version })
        override suspend fun deleteNegativesFor(personId: String) { negatives.removeAll { it.personId == personId } }
        override suspend fun allPeople(): List<PersonEntity> = people
        override fun observePerson(personId: String): Flow<PersonWithCount?> =
            flowOf(people.firstOrNull { it.id == personId }?.let { PersonWithCount(it, 0) })
        override suspend fun updateName(personId: String, name: String) {}
        override suspend fun setGender(personId: String, gender: String?) {}
        override suspend fun recordRecognition(personId: String, ts: Long, confidence: Double) {}
        override suspend fun setEnabled(personId: String, enabled: Boolean) {
            val idx = people.indexOfFirst { it.id == personId }
            if (idx >= 0) people[idx] = people[idx].copy(enabled = enabled)
        }
        override suspend fun setBlocked(personId: String, blocked: Boolean) {
            val idx = people.indexOfFirst { it.id == personId }
            if (idx >= 0) people[idx] = people[idx].copy(blocked = blocked)
        }
        override suspend fun deleteSamples(personId: String) { samples.removeAll { it.personId == personId } }
        override suspend fun deletePerson(personId: String) { people.removeAll { it.id == personId } }
    }

    private fun person(id: String, enabled: Boolean = true, blocked: Boolean = false) =
        PersonEntity(id = id, name = id, photoPath = null, createdAt = 0L, enabled = enabled, blocked = blocked)

    private fun sample(personId: String, vec: FloatArray) =
        FaceSampleEntity(
            "s-$personId", personId, EmbeddingMath.toBytes(EmbeddingMath.l2Normalize(vec)), 1f, 0L,
            modelVersion = EmbeddingMath.VERSION,
        )

    private fun neg(vec: FloatArray) =
        NegativeFaceEntity(
            "n-${vec.contentHashCode()}", null, EmbeddingMath.toBytes(EmbeddingMath.l2Normalize(vec)), null, 0L,
            modelVersion = EmbeddingMath.VERSION,
        )

    /** Helper: identify a single current-pipeline probe (samples in these tests are current version). */
    private suspend fun FaceRecognizer.identifyV(probe: FloatArray, sensitivity: Float, thresholdBoost: Float = 0f) =
        identify(mapOf(EmbeddingMath.VERSION to probe), sensitivity, thresholdBoost)

    @Test
    fun thresholdFor_mapsSensitivityRange() {
        val rec = FaceRecognizer(PeopleRepository(FakeDao()))
        assertEquals(0.35f, rec.thresholdFor(0f), 1e-5f)
        assertEquals(0.80f, rec.thresholdFor(1f), 1e-5f)
        assertEquals(0.575f, rec.thresholdFor(0.5f), 1e-5f)
    }

    @Test
    fun thresholdFor_clampsOutOfRange() {
        val rec = FaceRecognizer(PeopleRepository(FakeDao()))
        assertEquals(0.35f, rec.thresholdFor(-1f), 1e-5f)
        assertEquals(0.80f, rec.thresholdFor(5f), 1e-5f)
    }

    @Test
    fun identify_emptyEnrollmentDoesNotMatch() = runBlocking {
        val rec = FaceRecognizer(PeopleRepository(FakeDao()))
        val match = rec.identifyV(floatArrayOf(1f, 0f, 0f), 0.5f)
        assertFalse(match.matched)
    }

    @Test
    fun identify_emptyEnrollmentFlagsNoOwners() = runBlocking {
        val rec = FaceRecognizer(PeopleRepository(FakeDao()))
        val match = rec.identifyV(floatArrayOf(1f, 0f, 0f), 0.5f)
        assertTrue(match.noEnrolledOwners)
    }

    @Test
    fun identify_allOwnersDisabledFlagsNoOwners() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice", enabled = false)),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(1f, 0f, 0f), 0.5f)
        assertTrue(match.noEnrolledOwners)
        assertFalse(match.matched)
    }

    @Test
    fun identify_withOwnersDoesNotFlagNoOwners() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice")),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(0f, 1f, 0f), 0.5f)
        assertFalse(match.noEnrolledOwners)
    }

    @Test
    fun identify_matchesNearestEnrolledPerson() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice")),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(0.98f, 0.05f, 0f), 0.5f)
        assertTrue(match.matched)
        assertEquals("alice", match.personName)
        assertEquals("alice", match.personId)
    }

    @Test
    fun identify_thresholdBoostRejectsBorderlineMatch() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice")),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        // Probe at cosine ~0.6 to the enrolled face: above the default threshold, but a low-light
        // boost should push it below and reject it.
        val probe = floatArrayOf(0.6f, 0.8f, 0f)
        assertTrue(rec.identifyV(probe, 0.5f).matched)
        assertFalse(rec.identifyV(probe, 0.5f, thresholdBoost = 0.1f).matched)
    }

    @Test
    fun identify_rejectsDissimilarProbe() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice")),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(0f, 1f, 0f), 0.5f)
        assertFalse(match.matched)
        // Name/id are only revealed on a positive match.
        assertEquals(null, match.personName)
    }

    @Test
    fun identify_matchesLegacyVersionSampleWithLegacyProbe() = runBlocking {
        // A sample enrolled with the old pipeline (version 0) still matches when the caller supplies a
        // version-0 probe — this is what keeps existing enrollments working after a pipeline upgrade.
        val legacy = FaceSampleEntity(
            "s-old", "alice", EmbeddingMath.toBytes(EmbeddingMath.l2Normalize(floatArrayOf(1f, 0f, 0f))),
            1f, 0L, modelVersion = 0,
        )
        val dao = FakeDao(mutableListOf(person("alice")), mutableListOf(legacy))
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identify(mapOf(0 to floatArrayOf(0.98f, 0.05f, 0f)), 0.5f)
        assertTrue(match.matched)
        assertEquals("alice", match.personId)
    }

    @Test
    fun identify_rejectsWhenCloserToDeclinedNegative() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice")),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
            mutableListOf(neg(floatArrayOf(0.98f, 0.05f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        // Probe equals a declined look-alike, which is closer than the owner sample -> reject.
        val match = rec.identifyV(floatArrayOf(0.98f, 0.05f, 0f), 0.5f)
        assertFalse(match.matched)
    }

    @Test
    fun identify_ignoresDisabledPeople() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("alice", enabled = false)),
            mutableListOf(sample("alice", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(1f, 0f, 0f), 0.5f)
        assertFalse(match.matched)
    }

    @Test
    fun identify_flagsBlockedPerson() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("badguy", blocked = true)),
            mutableListOf(sample("badguy", floatArrayOf(1f, 0f, 0f))),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(0.98f, 0.05f, 0f), 0.5f)
        assertTrue(match.blocked)
        assertFalse(match.matched)
        assertEquals("badguy", match.personId)
    }

    @Test
    fun identify_blockedTakesPrecedenceOverLookAlike() = runBlocking {
        // Sibling (blocked) is closer to the probe than the owner (authorized).
        val dao = FakeDao(
            mutableListOf(person("owner"), person("sibling", blocked = true)),
            mutableListOf(
                sample("owner", floatArrayOf(0f, 1f, 0f)),
                sample("sibling", floatArrayOf(1f, 0f, 0f)),
            ),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(0.96f, 0.1f, 0f), 0.5f)
        assertTrue(match.blocked)
        assertEquals("sibling", match.personId)
    }

    @Test
    fun identify_ownerStillMatchesWhenClearlyCloser() = runBlocking {
        val dao = FakeDao(
            mutableListOf(person("owner"), person("sibling", blocked = true)),
            mutableListOf(
                sample("owner", floatArrayOf(1f, 0f, 0f)),
                sample("sibling", floatArrayOf(0f, 1f, 0f)),
            ),
        )
        val rec = FaceRecognizer(PeopleRepository(dao))
        val match = rec.identifyV(floatArrayOf(0.98f, 0.05f, 0f), 0.5f)
        assertTrue(match.matched)
        assertFalse(match.blocked)
        assertEquals("owner", match.personId)
    }
}


package com.guardia.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM face_samples WHERE face_samples.personId = people.id) AS sampleCount " +
            "FROM people ORDER BY createdAt DESC"
    )
    fun observePeople(): Flow<List<PersonWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<FaceSampleEntity>)

    @Query("SELECT * FROM face_samples")
    suspend fun allSamples(): List<FaceSampleEntity>

    @Query("SELECT * FROM face_samples WHERE personId = :personId ORDER BY createdAt DESC")
    fun observeSamples(personId: String): Flow<List<FaceSampleEntity>>

    @Query("SELECT * FROM face_samples WHERE personId = :personId")
    suspend fun samplesFor(personId: String): List<FaceSampleEntity>

    @Query("DELETE FROM face_samples WHERE id = :id")
    suspend fun deleteSample(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNegatives(negatives: List<NegativeFaceEntity>)

    @Query("SELECT * FROM negative_faces")
    suspend fun allNegatives(): List<NegativeFaceEntity>

    @Query("SELECT COUNT(*) FROM face_samples")
    fun observeTotalSampleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM face_samples WHERE modelVersion = :version")
    fun observeUsableSampleCount(version: Int): Flow<Int>

    @Query("DELETE FROM negative_faces WHERE personId = :personId")
    suspend fun deleteNegativesFor(personId: String)

    @Query("SELECT * FROM people")
    suspend fun allPeople(): List<PersonEntity>

    @Query(
        "SELECT *, (SELECT COUNT(*) FROM face_samples WHERE face_samples.personId = people.id) AS sampleCount " +
            "FROM people WHERE id = :personId LIMIT 1"
    )
    fun observePerson(personId: String): Flow<PersonWithCount?>

    @Query("UPDATE people SET name = :name WHERE id = :personId")
    suspend fun updateName(personId: String, name: String)

    @Query("UPDATE people SET gender = :gender WHERE id = :personId")
    suspend fun setGender(personId: String, gender: String?)

    @Query(
        "UPDATE people SET lastSeenAt = :ts, recognitionCount = recognitionCount + 1, " +
            "confidenceSum = confidenceSum + :confidence WHERE id = :personId"
    )
    suspend fun recordRecognition(personId: String, ts: Long, confidence: Double)

    @Query("UPDATE people SET enabled = :enabled WHERE id = :personId")
    suspend fun setEnabled(personId: String, enabled: Boolean)

    @Query("UPDATE people SET blocked = :blocked WHERE id = :personId")
    suspend fun setBlocked(personId: String, blocked: Boolean)

    @Query("DELETE FROM face_samples WHERE personId = :personId")
    suspend fun deleteSamples(personId: String)

    @Query("DELETE FROM people WHERE id = :personId")
    suspend fun deletePerson(personId: String)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT 300")
    fun observeEvents(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Dao
interface SafeZoneDao {
    @Query("SELECT * FROM safe_zones ORDER BY createdAt DESC")
    fun observeZones(): Flow<List<SafeZoneEntity>>

    @Query("SELECT * FROM safe_zones")
    suspend fun allZones(): List<SafeZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(zone: SafeZoneEntity)

    @Query("UPDATE safe_zones SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE safe_zones SET radiusMeters = :radius WHERE id = :id")
    suspend fun setRadius(id: String, radius: Int)

    @Query("UPDATE safe_zones SET guardEnabled = :enabled WHERE id = :id")
    suspend fun setGuardEnabled(id: String, enabled: Boolean)

    @Query("UPDATE safe_zones SET useDefault = :useDefault WHERE id = :id")
    suspend fun setUseDefault(id: String, useDefault: Boolean)

    @Query("UPDATE safe_zones SET responsiveness = :level WHERE id = :id")
    suspend fun setResponsiveness(id: String, level: Int)

    @Query("UPDATE safe_zones SET customIntervalSeconds = :seconds WHERE id = :id")
    suspend fun setCustomIntervalSeconds(id: String, seconds: Int)

    @Query("UPDATE safe_zones SET firstCheckOnUnlock = :enabled WHERE id = :id")
    suspend fun setFirstCheckOnUnlock(id: String, enabled: Boolean)

    @Query("UPDATE safe_zones SET checkRamp = :ramp WHERE id = :id")
    suspend fun setCheckRamp(id: String, ramp: String)

    @Query("UPDATE safe_zones SET shakeToCheck = :enabled WHERE id = :id")
    suspend fun setShakeToCheck(id: String, enabled: Boolean)

    @Query("UPDATE safe_zones SET lockOnNoFace = :enabled WHERE id = :id")
    suspend fun setLockOnNoFace(id: String, enabled: Boolean)

    @Query("DELETE FROM safe_zones WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface IntruderDao {
    @Query("SELECT * FROM intruder_captures ORDER BY timestamp DESC")
    fun observeCaptures(): Flow<List<IntruderCaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: IntruderCaptureEntity)

    @Query("SELECT * FROM intruder_captures WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): IntruderCaptureEntity?

    @Query("SELECT * FROM intruder_captures")
    suspend fun all(): List<IntruderCaptureEntity>

    @Query("DELETE FROM intruder_captures WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM intruder_captures")
    suspend fun clear()
}

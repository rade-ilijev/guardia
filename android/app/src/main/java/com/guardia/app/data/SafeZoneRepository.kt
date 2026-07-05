package com.guardia.app.data

import com.guardia.app.data.db.SafeZoneDao
import com.guardia.app.data.db.SafeZoneEntity
import com.guardia.app.domain.model.SafeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafeZoneRepository @Inject constructor(
    private val dao: SafeZoneDao,
) {
    val zones: Flow<List<SafeZone>> = dao.observeZones().map { rows -> rows.map { it.toDomain() } }

    suspend fun snapshot(): List<SafeZone> = dao.allZones().map { it.toDomain() }

    /** Adds a zone at the given coordinates. Returns the new id. */
    suspend fun add(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 150,
        guardEnabled: Boolean = false,
        responsiveness: Int = 1,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            SafeZoneEntity(
                id = id,
                name = name.ifBlank { "Safe zone" },
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters.coerceIn(MIN_RADIUS, MAX_RADIUS),
                guardEnabled = guardEnabled,
                useDefault = true,
                responsiveness = responsiveness.coerceIn(0, 2),
                customIntervalSeconds = 0,
                firstCheckOnUnlock = false,
                checkRamp = "",
                shakeToCheck = false,
                lockOnNoFace = false,
                createdAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    suspend fun rename(id: String, name: String) = dao.rename(id, name.ifBlank { "Safe zone" })
    suspend fun setRadius(id: String, radius: Int) = dao.setRadius(id, radius.coerceIn(MIN_RADIUS, MAX_RADIUS))
    suspend fun setGuardEnabled(id: String, enabled: Boolean) = dao.setGuardEnabled(id, enabled)
    suspend fun setUseDefault(id: String, useDefault: Boolean) = dao.setUseDefault(id, useDefault)
    suspend fun setResponsiveness(id: String, level: Int) = dao.setResponsiveness(id, level.coerceIn(0, 2))
    suspend fun setCustomIntervalSeconds(id: String, seconds: Int) = dao.setCustomIntervalSeconds(id, seconds.coerceAtLeast(0))
    suspend fun setFirstCheckOnUnlock(id: String, enabled: Boolean) = dao.setFirstCheckOnUnlock(id, enabled)
    suspend fun setCheckRamp(id: String, ramp: String) = dao.setCheckRamp(id, ramp)
    suspend fun setShakeToCheck(id: String, enabled: Boolean) = dao.setShakeToCheck(id, enabled)
    suspend fun setLockOnNoFace(id: String, enabled: Boolean) = dao.setLockOnNoFace(id, enabled)
    suspend fun delete(id: String) = dao.delete(id)

    private fun SafeZoneEntity.toDomain() = SafeZone(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        guardEnabled = guardEnabled,
        useDefault = useDefault,
        responsiveness = responsiveness,
        customIntervalSeconds = customIntervalSeconds,
        firstCheckOnUnlock = firstCheckOnUnlock,
        checkRamp = checkRamp,
        shakeToCheck = shakeToCheck,
        lockOnNoFace = lockOnNoFace,
        createdAt = createdAt,
    )

    companion object {
        const val MIN_RADIUS = 50
        const val MAX_RADIUS = 2000
    }
}

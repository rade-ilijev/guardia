package com.guardia.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val photoPath: String?,
    val createdAt: Long,
    /** Last time this person was recognized by the guard loop. */
    val lastSeenAt: Long? = null,
    /** Number of recognition samples recorded for this person. */
    val recognitionCount: Int = 0,
    /** Running sum of match confidence (0..1) used to compute the average. */
    val confidenceSum: Double = 0.0,
    /** When false, this person is ignored by the recognizer (treated as unauthorized). */
    val enabled: Boolean = true,
    /** When true, this is a known *unauthorized* person (block list): matching them locks the device. */
    val blocked: Boolean = false,
    /** Optional self-declared sex captured at enrollment ("MALE"/"FEMALE"), or null if unspecified. */
    val gender: String? = null,
)

@Entity(
    tableName = "face_samples",
    indices = [Index("personId")],
)
data class FaceSampleEntity(
    @PrimaryKey val id: String,
    val personId: String,
    /** L2-normalized embedding serialized as bytes (4 bytes per float). */
    val embedding: ByteArray,
    val quality: Float,
    val createdAt: Long,
    /** Optional cropped face image (app-private path) so the profile can show what it trained on. */
    val photoPath: String? = null,
    /** Embedding pipeline version this sample was produced with (see EmbeddingMath.VERSION). */
    val modelVersion: Int = 0,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is FaceSampleEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}

/**
 * A face the user explicitly marked as "not this person" while reviewing gallery photos. These are
 * used as negatives by the recognizer: a probe that looks more like a known negative than like the
 * owner is rejected, which sharpens recognition against look-alikes the user has seen and declined.
 */
@Entity(tableName = "negative_faces")
data class NegativeFaceEntity(
    @PrimaryKey val id: String,
    /** The profile this face was declined for, or null if declined globally. */
    val personId: String?,
    val embedding: ByteArray,
    val photoPath: String? = null,
    val createdAt: Long,
    /** Embedding pipeline version this negative was produced with (see EmbeddingMath.VERSION). */
    val modelVersion: Int = 0,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is NegativeFaceEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val message: String,
    val timestamp: Long,
    val photoPath: String?,
)

@Entity(tableName = "intruder_captures")
data class IntruderCaptureEntity(
    @PrimaryKey val id: String,
    val photoPath: String,
    val source: String,
    val timestamp: Long,
)

/**
 * A user-defined geographic area. Inside its [radiusMeters], guarding follows this zone's policy
 * ([guardEnabled] + [responsiveness]); outside every zone, the "public" policy in preferences applies.
 */
@Entity(tableName = "safe_zones")
data class SafeZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** Whether periodic background checks run while inside this zone. */
    val guardEnabled: Boolean,
    /** When true, this zone uses the global Guarding & Triggers schedule (ignoring the fields below). */
    val useDefault: Boolean = true,
    /** Check cadence inside this zone when guarding: 0 = saver, 1 = balanced, 2 = max. */
    val responsiveness: Int,
    /** Custom interval (seconds) inside this zone; 0 = use the responsiveness profile. */
    val customIntervalSeconds: Int = 0,
    /** Run a check the instant the device is unlocked while in this zone. */
    val firstCheckOnUnlock: Boolean = false,
    /** Comma-separated ramp gaps (seconds) after the unlock check, while in this zone. */
    val checkRamp: String = "",
    /** Trigger a check on shake while in this zone. */
    val shakeToCheck: Boolean = false,
    /** Lock when no face is visible while inside this zone. */
    val lockOnNoFace: Boolean = false,
    val createdAt: Long,
)

/** Person plus its enrolled-sample count. */
data class PersonWithCount(
    @Embedded val person: PersonEntity,
    val sampleCount: Int,
)

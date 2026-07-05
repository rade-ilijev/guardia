package com.guardia.app.domain.model

/** A geographic area with its own guarding policy. */
data class SafeZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val guardEnabled: Boolean,
    val useDefault: Boolean,
    val responsiveness: Int,
    val customIntervalSeconds: Int,
    val firstCheckOnUnlock: Boolean,
    val checkRamp: String,
    val shakeToCheck: Boolean,
    val lockOnNoFace: Boolean,
    val createdAt: Long,
)

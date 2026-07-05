package com.guardia.app.domain.model

/** A captured intruder photo (encrypted on disk). */
data class IntruderCapture(
    val id: String,
    val photoPath: String,
    val source: String,
    val timestamp: Long,
)

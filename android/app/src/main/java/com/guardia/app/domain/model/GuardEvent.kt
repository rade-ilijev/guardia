package com.guardia.app.domain.model

/** An event in the activity log (intruder lock, guarding toggled, enrollment, etc.). */
data class GuardEvent(
    val id: String,
    val type: Type,
    val message: String,
    val timestamp: Long,
    val photoPath: String? = null,
) {
    enum class Type { GUARDING_STARTED, GUARDING_STOPPED, INTRUDER_LOCK, UNKNOWN_FACE, WRONG_UNLOCK, ENROLLMENT, INFO }
}

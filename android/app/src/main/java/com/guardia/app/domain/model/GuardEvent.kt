package com.guardia.app.domain.model

/** An event in the activity log (intruder lock, guarding toggled, enrollment, etc.). */
data class GuardEvent(
    val id: String,
    val type: Type,
    val message: String,
    val timestamp: Long,
    val photoPath: String? = null,
) {
    enum class Type {
        GUARDING_STARTED,
        GUARDING_STOPPED,
        INTRUDER_LOCK,
        UNKNOWN_FACE,
        WRONG_UNLOCK,
        ENROLLMENT,
        /**
         * A lock the owner told us was actually them ("that was me"), which then became a training
         * sample. Distinct from [ENROLLMENT] so the false-lock rate can be counted rather than
         * inferred from message text — it is the one number that says whether guarding is too
         * suspicious of its owner.
         */
        FALSE_LOCK,
        INFO,
    }
}

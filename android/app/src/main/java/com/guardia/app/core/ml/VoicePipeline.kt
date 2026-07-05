package com.guardia.app.core.ml

/**
 * On-device voice safeword: keyword spotting for the secret phrase + speaker
 * verification (owner voiceprint). Used ONLY to start/stop guarding, including the
 * inconclusive-check fallback (open mic ~5-10s before locking). See master plan Screen 4.
 *
 * Scaffold interface — concrete implementation added in Phase 2.
 */
interface VoicePipeline {

    enum class Result { START, STOP, NO_MATCH }

    /** Listen for the safeword for [windowMs]; returns the detected intent. */
    suspend fun listen(windowMs: Long): Result
}

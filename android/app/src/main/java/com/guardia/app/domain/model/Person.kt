package com.guardia.app.domain.model

/** An enrolled, authorized person. Face embeddings live encrypted on-device (added in ML phase). */
data class Person(
    val id: String,
    val name: String,
    val sampleCount: Int,
    val photoPath: String?,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val recognitionCount: Int = 0,
    /** Average match confidence across recorded recognitions, 0..1. */
    val avgConfidence: Float = 0f,
    val enabled: Boolean = true,
    /** True for known unauthorized people (block list): matching them always locks the device. */
    val blocked: Boolean = false,
)

/** A single enrolled face sample, with the optional cropped image it was trained from. */
data class FaceSample(
    val id: String,
    val photoPath: String?,
    val createdAt: Long,
)

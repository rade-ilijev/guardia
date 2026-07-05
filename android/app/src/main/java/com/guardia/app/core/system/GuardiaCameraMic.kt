package com.guardia.app.core.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide registry of Guardia's *own* camera and microphone usage. The privacy monitor uses
 * this to tell "us" apart from another app: the OS tells us a camera/mic is in use but not by whom,
 * so we bracket every Guardia camera/mic session with [enterCamera]/[exitCamera] (and the mic
 * equivalents) and treat any usage that isn't ours as "another app".
 */
object GuardiaCameraMic {
    private val cameraCount = AtomicInteger(0)
    private val micCount = AtomicInteger(0)

    private val _selfCamera = MutableStateFlow(false)
    private val _selfMic = MutableStateFlow(false)
    val selfCamera: StateFlow<Boolean> = _selfCamera.asStateFlow()
    val selfMic: StateFlow<Boolean> = _selfMic.asStateFlow()

    val isSelfCamera: Boolean get() = cameraCount.get() > 0
    val isSelfMic: Boolean get() = micCount.get() > 0

    fun enterCamera() { _selfCamera.value = cameraCount.incrementAndGet() > 0 }
    fun exitCamera() { _selfCamera.value = cameraCount.updateAndGet { (it - 1).coerceAtLeast(0) } > 0 }
    fun enterMic() { _selfMic.value = micCount.incrementAndGet() > 0 }
    fun exitMic() { _selfMic.value = micCount.updateAndGet { (it - 1).coerceAtLeast(0) } > 0 }
}

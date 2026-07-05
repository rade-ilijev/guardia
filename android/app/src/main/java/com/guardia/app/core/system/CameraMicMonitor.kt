package com.guardia.app.core.system

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches whether *any* app is currently using the camera or microphone and, by cross-referencing
 * [GuardiaCameraMic] (Guardia's own usage), reports when it's some **other** app — a simple privacy
 * check so the user can spot a rogue app spying through the camera/mic.
 *
 * Uses only public, permission-free signals:
 *  - [CameraManager.AvailabilityCallback]: a camera going "unavailable" means an app opened it.
 *  - [AudioManager.AudioRecordingCallback]: active recording configurations mean the mic is in use.
 *
 * Neither API reveals which third-party app is responsible (by design), so attribution is limited to
 * "Guardia" vs "another app". Register with [start]; release with [stop] (reference-counted).
 */
@Singleton
class CameraMicMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class State(
        val cameraActive: Boolean = false,
        val micActive: Boolean = false,
        val selfCamera: Boolean = false,
        val selfMic: Boolean = false,
    ) {
        val otherCamera: Boolean get() = cameraActive && !selfCamera
        val otherMic: Boolean get() = micActive && !selfMic
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val unavailableCameras = mutableSetOf<String>()
    private val rawCamera = MutableStateFlow(false)
    private val rawMic = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        rawCamera, rawMic, GuardiaCameraMic.selfCamera, GuardiaCameraMic.selfMic,
    ) { cam, mic, selfCam, selfMic ->
        State(cameraActive = cam, micActive = mic, selfCamera = selfCam, selfMic = selfMic)
    }.stateIn(scope, SharingStarted.Eagerly, State())

    private var refs = 0

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            unavailableCameras.remove(cameraId)
            pushCamera(unavailableCameras.isNotEmpty())
        }
        override fun onCameraUnavailable(cameraId: String) {
            unavailableCameras.add(cameraId)
            pushCamera(true)
        }
    }

    private val audioCallback: AudioManager.AudioRecordingCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>?) {
                    pushMic(!configs.isNullOrEmpty())
                }
            } else null

    @Synchronized
    fun start() {
        if (refs++ > 0) return
        runCatching { cameraManager?.registerAvailabilityCallback(cameraCallback, handler) }
        val cb = audioCallback
        if (cb != null) {
            runCatching { audioManager?.registerAudioRecordingCallback(cb, handler) }
            // Seed current mic state.
            runCatching { pushMic(audioManager?.activeRecordingConfigurations?.isNotEmpty() == true) }
        }
    }

    @Synchronized
    fun stop() {
        if (refs <= 0) return
        if (--refs > 0) return
        runCatching { cameraManager?.unregisterAvailabilityCallback(cameraCallback) }
        val cb = audioCallback
        if (cb != null) runCatching { audioManager?.unregisterAudioRecordingCallback(cb) }
        unavailableCameras.clear()
        rawCamera.value = false
        rawMic.value = false
    }

    // Debounce turning *on* so our own enter/exit flags (set when we bind the camera) win the race
    // and we don't briefly flag ourselves as "another app". Turning off is immediate.
    private val cameraOn = Runnable { rawCamera.value = true }
    private fun pushCamera(active: Boolean) {
        handler.removeCallbacks(cameraOn)
        if (active) handler.postDelayed(cameraOn, ACTIVE_DEBOUNCE_MS) else rawCamera.value = false
    }

    private val micOn = Runnable { rawMic.value = true }
    private fun pushMic(active: Boolean) {
        handler.removeCallbacks(micOn)
        if (active) handler.postDelayed(micOn, ACTIVE_DEBOUNCE_MS) else rawMic.value = false
    }

    private companion object {
        const val ACTIVE_DEBOUNCE_MS = 700L
    }
}

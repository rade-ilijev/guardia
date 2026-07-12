package com.guardia.app.ui.screens.people

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.ml.BitmapUtils
import com.guardia.app.core.ml.EmbeddingMath
import com.guardia.app.core.ml.FaceEmbedder
import com.guardia.app.core.ml.FaceQualityAnalyzer
import com.guardia.app.core.ml.FaceRecognizer
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

enum class EnrollPhase { READY, CAPTURING, VERIFYING, VERIFIED, SAVED }

/** A single guided capture step: hold the requested pose until enough samples are collected. */
data class PoseStep(
    val pose: FaceQualityAnalyzer.HeadPose,
    val label: String,
    val instruction: String,
)

val ENROLL_POSES = listOf(
    PoseStep(FaceQualityAnalyzer.HeadPose.CENTER, "Front", "Look straight at the camera"),
    PoseStep(FaceQualityAnalyzer.HeadPose.RIGHT, "Right", "Slowly turn your head right"),
    PoseStep(FaceQualityAnalyzer.HeadPose.LEFT, "Left", "Slowly turn your head left"),
    PoseStep(FaceQualityAnalyzer.HeadPose.UP, "Up", "Tilt your head up a little"),
    PoseStep(FaceQualityAnalyzer.HeadPose.DOWN, "Down", "Tilt your head down a little"),
)

data class EnrollUiState(
    val phase: EnrollPhase = EnrollPhase.READY,
    val stepIndex: Int = 0,
    val totalSteps: Int = ENROLL_POSES.size,
    val collectedInStep: Int = 0,
    val perStepTarget: Int = 2,
    val completedPoses: Set<FaceQualityAnalyzer.HeadPose> = emptySet(),
    val requiredPose: FaceQualityAnalyzer.HeadPose? = null,
    val message: String = "Position your face in the circle",
    val score: Float? = null,
) {
    /** Overall progress across all poses, 0..1. */
    val progress: Float
        get() = ((stepIndex.toFloat()) + collectedInStep.toFloat() / perStepTarget) / totalSteps
}

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quality: FaceQualityAnalyzer,
    private val embedder: FaceEmbedder,
    private val recognizer: FaceRecognizer,
    private val people: PeopleRepository,
    private val events: EventsRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(EnrollUiState())
    val ui: StateFlow<EnrollUiState> = _ui.asStateFlow()

    private val embeddings = mutableListOf<FloatArray>()
    private val photos = mutableListOf<String?>()
    private var photoPath: String? = null
    private var sensitivity = 0.5f
    private var lastCaptureAt = 0L
    private val processing = AtomicBoolean(false)

    init {
        viewModelScope.launch { sensitivity = prefs.sensitivity.first() }
    }

    fun start() = beginCapture()

    fun retry() = beginCapture()

    private fun beginCapture() {
        embeddings.clear()
        photos.clear()
        photoPath = null
        val first = ENROLL_POSES.first()
        _ui.value = EnrollUiState(
            phase = EnrollPhase.CAPTURING,
            stepIndex = 0,
            requiredPose = first.pose,
            message = first.instruction,
        )
    }

    fun onFrame(bitmap: Bitmap, rotation: Int) {
        val phase = _ui.value.phase
        if (phase != EnrollPhase.CAPTURING && phase != EnrollPhase.VERIFYING) return
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                process(bitmap, rotation)
            } finally {
                processing.set(false)
            }
        }
    }

    private suspend fun process(bitmap: Bitmap, rotation: Int) {
        val upright = BitmapUtils.rotate(bitmap, rotation)
        val faces = quality.detect(upright)
        if (faces.size != 1) {
            update(message = if (faces.isEmpty()) "No face detected" else "Only one person in frame, please")
            return
        }
        val face = faces[0]
        val state = _ui.value

        when (state.phase) {
            EnrollPhase.CAPTURING -> {
                val step = ENROLL_POSES.getOrNull(state.stepIndex) ?: return
                val q = quality.assessBasics(face, upright, requireEyesOpen = step.pose == FaceQualityAnalyzer.HeadPose.CENTER)
                if (!q.ok) {
                    update(message = q.reason)
                    return
                }
                val pose = quality.poseOf(face)
                if (pose != step.pose) {
                    update(message = step.instruction)
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastCaptureAt < 300) return
                lastCaptureAt = now

                val aligned = com.guardia.app.core.ml.FaceAligner.align(upright, face)
                val path = savePhoto(aligned)
                embeddings.add(embedder.embed(aligned))
                photos.add(path)
                // Use the straight-on shot as the person's avatar.
                if (step.pose == FaceQualityAnalyzer.HeadPose.CENTER && photoPath == null) photoPath = path

                val collected = state.collectedInStep + 1
                if (collected >= state.perStepTarget) {
                    advanceStep(state)
                } else {
                    _ui.value = state.copy(collectedInStep = collected, message = "Hold it…")
                }
            }
            EnrollPhase.VERIFYING -> {
                val q = quality.assessBasics(face, upright, requireEyesOpen = true)
                if (!q.ok) { update(message = q.reason); return }
                if (quality.poseOf(face) != FaceQualityAnalyzer.HeadPose.CENTER) {
                    update(message = "Look straight at the camera to confirm")
                    return
                }
                val embedding = embedder.embed(com.guardia.app.core.ml.FaceAligner.align(upright, face))
                val sim = embeddings.maxOf { EmbeddingMath.cosine(embedding, it) }
                if (sim >= recognizer.thresholdFor(sensitivity)) {
                    _ui.value = _ui.value.copy(
                        phase = EnrollPhase.VERIFIED,
                        score = sim,
                        message = "Recognized you — ${(sim * 100).toInt()}% match",
                    )
                } else {
                    update(message = "Hold still to verify…")
                }
            }
            else -> Unit
        }
    }

    private fun advanceStep(state: EnrollUiState) {
        val completed = state.completedPoses + ENROLL_POSES[state.stepIndex].pose
        val next = state.stepIndex + 1
        if (next >= ENROLL_POSES.size) {
            _ui.value = state.copy(
                phase = EnrollPhase.VERIFYING,
                completedPoses = completed,
                collectedInStep = 0,
                requiredPose = FaceQualityAnalyzer.HeadPose.CENTER,
                message = "Almost done — look straight ahead",
            )
        } else {
            val step = ENROLL_POSES[next]
            _ui.value = state.copy(
                stepIndex = next,
                collectedInStep = 0,
                completedPoses = completed,
                requiredPose = step.pose,
                message = step.instruction,
            )
        }
    }

    fun save(name: String, gender: String?, existingPersonId: String?, onDone: () -> Unit) {
        if (_ui.value.phase != EnrollPhase.VERIFIED || embeddings.isEmpty()) return
        viewModelScope.launch {
            val targetId = existingPersonId
                ?: people.addPerson(name = name, photoPath = photoPath, embeddings = emptyList(), gender = gender)
            embeddings.forEachIndexed { i, e -> people.addSample(targetId, e, photos.getOrNull(i)) }
            if (existingPersonId != null) {
                events.log(GuardEvent.Type.ENROLLMENT, "Added ${embeddings.size} samples")
            } else {
                events.log(GuardEvent.Type.ENROLLMENT, "Enrolled $name (${embeddings.size} samples)")
            }
            _ui.value = _ui.value.copy(phase = EnrollPhase.SAVED)
            onDone()
        }
    }

    private fun update(message: String) {
        _ui.value = _ui.value.copy(message = message)
    }

    private fun savePhoto(crop: Bitmap): String {
        val dir = File(context.filesDir, "faces").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        ByteArrayOutputStream().use { stream ->
            crop.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            file.writeBytes(stream.toByteArray())
        }
        return file.absolutePath
    }
}

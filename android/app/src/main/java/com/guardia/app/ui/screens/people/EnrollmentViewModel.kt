package com.guardia.app.ui.screens.people

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

/** An already-enrolled person the freshly captured face strongly resembles. */
data class DuplicateHit(
    val personId: String,
    val name: String,
    val similarity: Float,
    val blocked: Boolean,
)

/**
 * Marked `@Immutable` for Compose: it holds a `List`, and Compose treats every `List` as unstable
 * because the interface allows a mutable implementation. Without the annotation, any composable
 * reading this state is re-run on *every* recomposition of its parent, even when the state itself
 * has not changed. The contents genuinely are never mutated after construction, so the promise is
 * safe to make — and it is what lets Compose skip the subtree.
 */
@Immutable
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
    /** Set after capture when this face closely matches someone already enrolled. */
    val duplicate: DuplicateHit? = null,
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
    /**
     * How good each capture was (0..1), stored alongside its embedding. The recognizer weights
     * samples by this, so the two or three softer frames that every enrollment session collects
     * still contribute pose coverage without pulling the person's prototype off-centre.
     */
    private val qualities = mutableListOf<Float>()
    private var photoPath: String? = null
    private var sensitivity = 0.5f
    private var lastCaptureAt = 0L
    private val processing = AtomicBoolean(false)
    /** Off when adding samples to a known person — matching them is expected, not a duplicate. */
    private var checkDuplicates = true

    /** The capture plan: full multi-angle enrollment, or the quick front-only guest scan. */
    private var poses: List<PoseStep> = ENROLL_POSES
    private var perStep = 2

    init {
        viewModelScope.launch { sensitivity = prefs.sensitivity.first() }
    }

    fun start(checkDuplicates: Boolean = true, quick: Boolean = false) {
        this.checkDuplicates = checkDuplicates
        if (quick) {
            // Guest pass: one pose, a few samples — good enough for hours, not forever.
            poses = listOf(PoseStep(FaceQualityAnalyzer.HeadPose.CENTER, "Front", "Look straight at the camera"))
            perStep = 3
        } else {
            poses = ENROLL_POSES
            perStep = 2
        }
        beginCapture()
    }

    fun retry() = beginCapture()

    /** The user said "someone new" — drop the duplicate suggestion and allow a normal save. */
    fun dismissDuplicate() {
        _ui.value = _ui.value.copy(duplicate = null)
    }

    private fun beginCapture() {
        embeddings.clear()
        photos.clear()
        qualities.clear()
        photoPath = null
        val first = poses.first()
        _ui.value = EnrollUiState(
            phase = EnrollPhase.CAPTURING,
            stepIndex = 0,
            totalSteps = poses.size,
            perStepTarget = perStep,
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
                val step = poses.getOrNull(state.stepIndex) ?: return
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
                qualities.add(captureQuality(q.score, aligned))
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
                        duplicate = if (checkDuplicates) findDuplicate() else null,
                    )
                } else {
                    update(message = "Hold still to verify…")
                }
            }
            else -> Unit
        }
    }

    private fun advanceStep(state: EnrollUiState) {
        val completed = state.completedPoses + poses[state.stepIndex].pose
        val next = state.stepIndex + 1
        if (next >= poses.size) {
            _ui.value = state.copy(
                phase = EnrollPhase.VERIFYING,
                completedPoses = completed,
                collectedInStep = 0,
                requiredPose = FaceQualityAnalyzer.HeadPose.CENTER,
                message = "Almost done — look straight ahead",
            )
        } else {
            val step = poses[next]
            _ui.value = state.copy(
                stepIndex = next,
                collectedInStep = 0,
                completedPoses = completed,
                requiredPose = step.pose,
                message = step.instruction,
            )
        }
    }

    /** Saves the verified capture as a temporary guest that stops being trusted after [durationMs]. */
    fun saveGuest(durationMs: Long, onDone: () -> Unit) {
        if (_ui.value.phase != EnrollPhase.VERIFIED || embeddings.isEmpty()) return
        viewModelScope.launch {
            val until = System.currentTimeMillis() + durationMs
            val id = people.addPerson(
                name = "Guest",
                photoPath = photoPath,
                embeddings = emptyList(),
                expiresAt = until,
            )
            embeddings.forEachIndexed { i, e ->
                people.addSample(id, e, photos.getOrNull(i), qualities.getOrElse(i) { 1f })
            }
            events.log(
                GuardEvent.Type.ENROLLMENT,
                "Guest pass created (${durationMs / 60_000} min)",
            )
            _ui.value = _ui.value.copy(phase = EnrollPhase.SAVED)
            onDone()
        }
    }

    fun save(name: String, gender: String?, existingPersonId: String?, onDone: () -> Unit) {
        if (_ui.value.phase != EnrollPhase.VERIFIED || embeddings.isEmpty()) return
        viewModelScope.launch {
            val targetId = existingPersonId
                ?: people.addPerson(name = name, photoPath = photoPath, embeddings = emptyList(), gender = gender)
            embeddings.forEachIndexed { i, e ->
                people.addSample(targetId, e, photos.getOrNull(i), qualities.getOrElse(i) { 1f })
            }
            if (existingPersonId != null) {
                events.log(GuardEvent.Type.ENROLLMENT, "Added ${embeddings.size} samples")
            } else {
                events.log(GuardEvent.Type.ENROLLMENT, "Enrolled $name (${embeddings.size} samples)")
            }
            _ui.value = _ui.value.copy(phase = EnrollPhase.SAVED)
            onDone()
        }
    }

    /**
     * Combines how well the face filled the frame ([basicsScore], from
     * [com.guardia.app.core.ml.FaceQualityAnalyzer.assessBasics]) with how crisp the aligned crop
     * actually is. Size alone is a poor proxy: a large but motion-blurred face makes a worse
     * reference than a smaller sharp one, and it is the blurred references that cause false accepts
     * later, because a blurred embedding sits close to everybody.
     */
    private fun captureQuality(basicsScore: Float, aligned: android.graphics.Bitmap): Float {
        val sharp = runCatching { BitmapUtils.sharpness(aligned) }.getOrDefault(SHARP_REFERENCE)
        val sharpScore = (sharp / SHARP_REFERENCE).coerceIn(0f, 1f)
        return (0.4f * basicsScore.coerceIn(0f, 1f) + 0.6f * sharpScore).coerceIn(0.1f, 1f)
    }

    /**
     * Best already-enrolled match for the freshly captured face, or null when nobody comes close.
     * Compares only same-pipeline-version samples (cross-version cosine is meaningless), and uses
     * a deliberately high bar so the "same person?" prompt only appears when it's probably right.
     */
    private suspend fun findDuplicate(): DuplicateHit? {
        val enrolled = people.enrolledFaces().filter { it.modelVersion == EmbeddingMath.VERSION }
        if (enrolled.isEmpty() || embeddings.isEmpty()) return null
        var best: DuplicateHit? = null
        for ((personId, faces) in enrolled.groupBy { it.personId }) {
            var sim = 0f
            for (f in faces) {
                for (e in embeddings) {
                    val c = EmbeddingMath.cosine(e, f.embedding)
                    if (c > sim) sim = c
                }
            }
            if (sim > (best?.similarity ?: 0f)) {
                best = DuplicateHit(personId, faces.first().name, sim, faces.first().blocked)
            }
        }
        return best?.takeIf { it.similarity >= DUPLICATE_THRESHOLD }
    }

    private fun update(message: String) {
        _ui.value = _ui.value.copy(message = message)
    }

    private companion object {
        /** Cosine bar for the "already enrolled?" prompt — high enough to rarely be wrong. */
        const val DUPLICATE_THRESHOLD = 0.60f

        /**
         * Sharpness (see [BitmapUtils.sharpness]) at which a capture is considered fully crisp.
         * Anything at or above this scores 1.0; below it, quality falls off proportionally.
         */
        const val SHARP_REFERENCE = 14f
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

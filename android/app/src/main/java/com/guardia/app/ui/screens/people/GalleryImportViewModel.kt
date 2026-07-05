package com.guardia.app.ui.screens.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.ml.EmbeddingMath
import com.guardia.app.core.ml.FaceAligner
import com.guardia.app.core.ml.FaceEmbedder
import com.guardia.app.core.ml.FaceQualityAnalyzer
import com.guardia.app.data.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

enum class ImportPhase { IDLE, SCANNING, REVIEW, DONE }

/** One detected face awaiting the user's swipe decision. */
data class ImportCandidate(
    val id: String,
    val photoPath: String,
    val embedding: FloatArray,
    val matchPercent: Int,
)

data class GalleryImportUi(
    val phase: ImportPhase = ImportPhase.IDLE,
    val scanned: Int = 0,
    val total: Int = 0,
    val candidates: List<ImportCandidate> = emptyList(),
    val index: Int = 0,
    val confirmed: Int = 0,
    val declined: Int = 0,
    val blacklisted: Int = 0,
) {
    val current: ImportCandidate? get() = candidates.getOrNull(index)
    val remaining: Int get() = (candidates.size - index).coerceAtLeast(0)
}

/**
 * Scans user-picked gallery photos for faces and lets the user triage each one for a profile:
 * confirm it's them (positive sample), decline (negative — "not them"), or blacklist it. More
 * confirmed angles and explicit negatives both improve recognition over time.
 */
@HiltViewModel
class GalleryImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val quality: FaceQualityAnalyzer,
    private val embedder: FaceEmbedder,
    private val people: PeopleRepository,
) : ViewModel() {

    val personId: String = savedStateHandle["personId"] ?: ""

    private val _ui = MutableStateFlow(GalleryImportUi())
    val ui: StateFlow<GalleryImportUi> = _ui.asStateFlow()

    val personName = MutableStateFlow("this profile")

    private var profileEmbeddings: List<FloatArray> = emptyList()

    init {
        viewModelScope.launch {
            people.person(personId).collect { p -> if (p != null) personName.value = p.name }
        }
    }

    fun scan(uris: List<Uri>) {
        if (uris.isEmpty() || _ui.value.phase == ImportPhase.SCANNING) return
        _ui.value = GalleryImportUi(phase = ImportPhase.SCANNING, total = uris.size)
        viewModelScope.launch(Dispatchers.Default) {
            profileEmbeddings = runCatching { people.personEmbeddings(personId) }.getOrDefault(emptyList())
            val found = mutableListOf<ImportCandidate>()
            for ((i, uri) in uris.withIndex()) {
                if (found.size >= MAX_CANDIDATES) break
                val bitmap = runCatching { decode(uri) }.getOrNull()
                if (bitmap != null) {
                    runCatching { extractFaces(bitmap, found) }
                }
                _ui.value = _ui.value.copy(scanned = i + 1, candidates = found.toList())
            }
            _ui.value = _ui.value.copy(
                phase = if (found.isEmpty()) ImportPhase.DONE else ImportPhase.REVIEW,
                candidates = found.toList(),
            )
        }
    }

    private suspend fun extractFaces(bitmap: Bitmap, out: MutableList<ImportCandidate>) {
        val faces = quality.detect(bitmap)
        for (face in faces) {
            if (out.size >= MAX_CANDIDATES) break
            val box = face.boundingBox
            val area = box.width().toFloat() * box.height()
            if (area / (bitmap.width.toFloat() * bitmap.height) < MIN_FACE_RATIO) continue
            val aligned = FaceAligner.align(bitmap, face)
            val embedding = embedder.embed(aligned)
            val match = if (profileEmbeddings.isEmpty()) 0
            else (profileEmbeddings.maxOf { EmbeddingMath.cosine(embedding, it) }.coerceIn(0f, 1f) * 100).roundToInt()
            val path = saveCrop(aligned)
            out.add(ImportCandidate(UUID.randomUUID().toString(), path, embedding, match))
        }
    }

    fun confirm() = act { c ->
        people.addSample(personId, c.embedding, c.photoPath)
        _ui.value = _ui.value.copy(confirmed = _ui.value.confirmed + 1)
    }

    fun decline() = act { c ->
        people.addNegative(personId, c.embedding, c.photoPath)
        _ui.value = _ui.value.copy(declined = _ui.value.declined + 1)
    }

    fun blacklist() = act { c ->
        people.addToBlacklist(c.embedding, c.photoPath)
        people.addNegative(null, c.embedding, c.photoPath)
        _ui.value = _ui.value.copy(blacklisted = _ui.value.blacklisted + 1)
    }

    private fun act(block: suspend (ImportCandidate) -> Unit) {
        val candidate = _ui.value.current ?: return
        viewModelScope.launch {
            runCatching { block(candidate) }
            advance()
        }
    }

    private fun advance() {
        val next = _ui.value.index + 1
        _ui.value = _ui.value.copy(
            index = next,
            phase = if (next >= _ui.value.candidates.size) ImportPhase.DONE else _ui.value.phase,
        )
    }

    private fun saveCrop(crop: Bitmap): String {
        val dir = File(context.filesDir, "faces").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        ByteArrayOutputStream().use { stream ->
            crop.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            file.writeBytes(stream.toByteArray())
        }
        return file.absolutePath
    }

    /** Decodes a gallery image downscaled to a sane size and rotated upright per EXIF. */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sample > MAX_DECODE_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        return if (degrees == 0) bitmap else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true,
        )
    }

    private companion object {
        const val MAX_CANDIDATES = 80
        const val MIN_FACE_RATIO = 0.01f
        const val MAX_DECODE_DIM = 1280
    }
}

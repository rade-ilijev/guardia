package com.guardia.app.ui.screens.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.ml.FaceAligner
import com.guardia.app.core.ml.FaceEmbedder
import com.guardia.app.core.ml.FaceQualityAnalyzer
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.GuardEvent
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

/** A face extracted from an imported photo, ready to enroll as a blocked sample. */
data class ExtractedFace(val thumbnail: ImageBitmap, val embedding: FloatArray)

data class BlockedUiState(
    val name: String = "",
    val faces: List<ExtractedFace> = emptyList(),
    val processing: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AddBlockedPersonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quality: FaceQualityAnalyzer,
    private val embedder: FaceEmbedder,
    private val people: PeopleRepository,
    private val events: EventsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BlockedUiState())
    val ui: StateFlow<BlockedUiState> = _ui.asStateFlow()

    fun setName(value: String) { _ui.value = _ui.value.copy(name = value) }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.value = _ui.value.copy(processing = true, message = null)
        viewModelScope.launch {
            var added = 0
            var failed = 0
            for (uri in uris) {
                val face = withContext(Dispatchers.Default) { extractFace(uri) }
                if (face != null) {
                    _ui.value = _ui.value.copy(faces = _ui.value.faces + face)
                    added++
                } else failed++
            }
            val msg = buildString {
                if (added > 0) append("Added $added face${if (added == 1) "" else "s"}.")
                if (failed > 0) append(" Couldn't find a face in $failed photo${if (failed == 1) "" else "s"}.")
            }.ifBlank { "No faces found." }
            _ui.value = _ui.value.copy(processing = false, message = msg)
        }
    }

    private suspend fun extractFace(uri: Uri): ExtractedFace? {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val faces = quality.detect(bitmap)
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val aligned = FaceAligner.align(bitmap, face)
        val embedding = embedder.embed(aligned)
        return ExtractedFace(aligned.asImageBitmap(), embedding)
    }

    fun removeFace(index: Int) {
        val list = _ui.value.faces.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _ui.value = _ui.value.copy(faces = list)
        }
    }

    fun save(onDone: () -> Unit) {
        val state = _ui.value
        if (state.name.isBlank() || state.faces.isEmpty() || state.processing) return
        viewModelScope.launch {
            val photoPath = withContext(Dispatchers.IO) { saveThumb(state.faces.first().thumbnail) }
            people.addPerson(
                name = state.name.trim(),
                photoPath = photoPath,
                embeddings = state.faces.map { it.embedding },
                blocked = true,
            )
            events.log(GuardEvent.Type.ENROLLMENT, "Added blocked person ${state.name.trim()} (${state.faces.size} samples)")
            _ui.value = state.copy(saved = true)
            onDone()
        }
    }

    private fun saveThumb(image: ImageBitmap): String? = runCatching {
        val bmp = image.asAndroidBitmap()
        val dir = File(context.filesDir, "faces").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        ByteArrayOutputStream().use { stream ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            file.writeBytes(stream.toByteArray())
        }
        file.absolutePath
    }.getOrNull()
}

package com.guardia.app.ui.screens.intruders

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.ml.FaceAligner
import com.guardia.app.core.ml.FaceEmbedder
import com.guardia.app.core.ml.FaceQualityAnalyzer
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.GuardEvent
import com.guardia.app.domain.model.IntruderCapture
import com.guardia.app.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class IntrudersViewModel @Inject constructor(
    private val repository: IntruderRepository,
    private val people: PeopleRepository,
    private val quality: FaceQualityAnalyzer,
    private val embedder: FaceEmbedder,
    private val events: EventsRepository,
) : ViewModel() {

    val captures: StateFlow<List<IntruderCapture>> = repository.captures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peopleList: StateFlow<List<Person>> = people.people
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadBitmap(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        decode(path)?.asImageBitmap()
    }

    private fun decode(path: String): Bitmap? {
        val bytes = repository.decrypt(path) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Detects the face in a captured photo, computes its embedding, and adds it as a
     * new sample for [personId]. Reports a human-readable result via [onResult].
     */
    fun assignToPerson(capture: IntruderCapture, personId: String, personName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val bitmap = decode(capture.photoPath) ?: return@withContext false to "Could not open photo"
                val faces = quality.detect(bitmap)
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    ?: return@withContext false to "No face found in this photo"
                val embedding = embedder.embed(FaceAligner.align(bitmap, face))
                people.addSamples(personId, listOf(embedding))
                true to "Added this face to $personName"
            }
            if (result.first) {
                events.log(GuardEvent.Type.ENROLLMENT, "Assigned captured face to $personName")
                repository.delete(capture.id)
            }
            onResult(result.first, result.second)
        }
    }

    /** Creates a new block-listed person from a captured photo's face. */
    fun createBlockedFromCapture(capture: IntruderCapture, name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val bitmap = decode(capture.photoPath) ?: return@withContext false to "Could not open photo"
                val faces = quality.detect(bitmap)
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    ?: return@withContext false to "No face found in this photo"
                val embedding = embedder.embed(FaceAligner.align(bitmap, face))
                people.addPerson(name = name.trim(), photoPath = null, embeddings = listOf(embedding), blocked = true)
                true to "Blocked ${name.trim()}"
            }
            if (result.first) {
                events.log(GuardEvent.Type.ENROLLMENT, "Blocked ${name.trim()} from a capture")
                repository.delete(capture.id)
            }
            onResult(result.first, result.second)
        }
    }

    fun delete(capture: IntruderCapture) {
        viewModelScope.launch { repository.delete(capture.id) }
    }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}

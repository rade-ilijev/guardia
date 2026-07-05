package com.guardia.app.ui.screens.activity

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: EventsRepository,
    private val intruders: IntruderRepository,
) : ViewModel() {

    val events: StateFlow<List<GuardEvent>> = repository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Decrypts the encrypted capture at [path] into a thumbnail, or null if unavailable. */
    suspend fun loadThumbnail(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val bytes = intruders.decrypt(path) ?: return@withContext null
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}

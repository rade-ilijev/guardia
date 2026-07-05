package com.guardia.app.ui.screens.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.FaceSample
import com.guardia.app.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
) : ViewModel() {

    val personId: String = savedStateHandle["personId"] ?: ""

    val person: StateFlow<Person?> = repository.person(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val samples: StateFlow<List<FaceSample>> = repository.samples(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSample(id: String) {
        viewModelScope.launch { repository.deleteSample(id) }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.rename(personId, name) }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(personId, enabled) }
    }

    fun setBlocked(blocked: Boolean) {
        viewModelScope.launch { repository.setBlocked(personId, blocked) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.remove(personId)
            onDone()
        }
    }
}

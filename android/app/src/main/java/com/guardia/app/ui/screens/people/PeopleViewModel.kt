package com.guardia.app.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    val people: StateFlow<List<Person>> = repository.people
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True when enrolled faces are from an older recognition pipeline and should be re-captured. */
    val needsReenroll: StateFlow<Boolean> = repository.needsReenroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }
}

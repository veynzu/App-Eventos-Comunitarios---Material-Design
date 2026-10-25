package com.eventoscomunitarios.app.features.event_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EventDetailUiState(
    val event: Event? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val isFollowing: Boolean = false
)

class EventDetailViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        val eventId: Int? = savedStateHandle["eventId"]
        loadEvent(eventId)
    }

    private fun loadEvent(eventId: Int?) {
        if (eventId == null) {
            _uiState.update { it.copy(isLoading = false, error = "ID de evento no válido") }
            return
        }

        val event = FakeEvents.events.firstOrNull { it.id == eventId }

        _uiState.update {
            if (event != null) {
                it.copy(event = event, isLoading = false, error = null)
            } else {
                it.copy(isLoading = false, error = "Evento no encontrado")
            }
        }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun toggleFollow() {
        _uiState.update { it.copy(isFollowing = !it.isFollowing) }
    }
}
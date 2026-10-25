package com.eventoscomunitarios.app.features.event_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EventDetailUiState(
    val event: Event? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val isFollowing: Boolean = false,
    val registrationStatus: EventRegistrationStatus? = null
)

class EventDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val eventId: Int? = savedStateHandle["eventId"]
    private val userId: String? = savedStateHandle["userId"]
    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        loadEvent()
    }

    fun confirmAttendance(): Boolean {
        val currentEvent = _uiState.value.event ?: return false
        val currentUserId = userId ?: return false
        val existingRegistration = FakeEvents.registrations.firstOrNull {
            it.userId == currentUserId && it.eventId == currentEvent.id
        }
        if (existingRegistration != null) {
            _uiState.update { it.copy(registrationStatus = existingRegistration.status) }
            return true
        }

        val status = if (currentEvent.attendeeCount < currentEvent.capacity) {
            EventRegistrationStatus.CONFIRMED
        } else {
            EventRegistrationStatus.WAITLIST
        }
        val updatedEvent = if (status == EventRegistrationStatus.CONFIRMED) {
            currentEvent.copy(attendeeCount = currentEvent.attendeeCount + 1)
        } else {
            currentEvent
        }

        FakeEvents.events = FakeEvents.events.map { event ->
            if (event.id == currentEvent.id) updatedEvent else event
        }
        FakeEvents.registrations = FakeEvents.registrations + EventRegistration(
            userId = currentUserId,
            eventId = currentEvent.id,
            status = status
        )
        _uiState.update { it.copy(event = updatedEvent, registrationStatus = status) }
        return true
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun toggleFollow() {
        _uiState.update { it.copy(isFollowing = !it.isFollowing) }
    }

    private fun loadEvent() {
        if (eventId == null || userId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Datos de navegación no válidos") }
            return
        }

        val event = FakeEvents.events.firstOrNull { it.id == eventId }
        val status = FakeEvents.registrations.firstOrNull {
            it.userId == userId && it.eventId == eventId
        }?.status

        _uiState.update {
            if (event != null) {
                it.copy(event = event, isLoading = false, error = null, registrationStatus = status)
            } else {
                it.copy(isLoading = false, error = "Evento no encontrado")
            }
        }
    }
}

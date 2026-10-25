package com.eventoscomunitarios.app.features.my_events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MyEventsTab { ENROLLED, CREATED }

data class EnrolledEvent(
    val event: Event,
    val status: EventRegistrationStatus
)

data class MyEventsUiState(
    val selectedTab: MyEventsTab = MyEventsTab.ENROLLED,
    val enrolledEvents: List<EnrolledEvent> = emptyList(),
    val createdEvents: List<Event> = emptyList(),
    val error: String? = null
)

class MyEventsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val userId: String? = savedStateHandle["userId"]
    private val _uiState = MutableStateFlow(MyEventsUiState())
    val uiState: StateFlow<MyEventsUiState> = _uiState.asStateFlow()

    init {
        updateEvents(FakeEvents.events, FakeEvents.registrations)
        viewModelScope.launch {
            combine(FakeEvents.eventsFlow, FakeEvents.registrationsFlow) { events, registrations ->
                events to registrations
            }.collect { (events, registrations) -> updateEvents(events, registrations) }
        }
    }

    fun onTabSelected(tab: MyEventsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun cancelEnrollment(eventId: Int) {
        val currentUserId = userId ?: return
        val registration = FakeEvents.registrations.firstOrNull {
            it.userId == currentUserId && it.eventId == eventId
        } ?: return

        if (registration.status == EventRegistrationStatus.CONFIRMED) {
            FakeEvents.events = FakeEvents.events.map { event ->
                if (event.id == eventId) {
                    event.copy(attendeeCount = (event.attendeeCount - 1).coerceAtLeast(0))
                } else {
                    event
                }
            }
        }
        FakeEvents.registrations = FakeEvents.registrations.filterNot {
            it.userId == currentUserId && it.eventId == eventId
        }
    }

    private fun updateEvents(events: List<Event>, registrations: List<EventRegistration>) {
        val currentUserId = userId
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(error = "No se pudo identificar al usuario de la sesión") }
            return
        }

        val eventsById = events.associateBy(Event::id)
        val enrolled = registrations
            .asSequence()
            .filter { it.userId == currentUserId }
            .mapNotNull { registration ->
                eventsById[registration.eventId]
                    ?.takeIf { it.moderationStatus == EventModerationStatus.APPROVED }
                    ?.let { event ->
                    EnrolledEvent(event, registration.status)
                }
            }
            .toList()

        _uiState.update {
            it.copy(
                enrolledEvents = enrolled,
                createdEvents = events.filter { event -> event.organizerId == currentUserId },
                error = null
            )
        }
    }
}

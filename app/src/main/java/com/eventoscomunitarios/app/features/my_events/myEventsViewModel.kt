package com.eventoscomunitarios.app.features.my_events

import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MyEventsTab { ENROLLED, CREATED }
enum class EnrollmentStatus { CONFIRMED, WAITLIST }

data class EnrolledEvent(
    val event: Event,
    val status: EnrollmentStatus
)

data class MyEventsUiState(
    val selectedTab: MyEventsTab = MyEventsTab.ENROLLED,
    val enrolledEvents: List<EnrolledEvent> = emptyList(),
    val createdEvents: List<Event> = emptyList()
)

class MyEventsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MyEventsUiState())
    val uiState: StateFlow<MyEventsUiState> = _uiState.asStateFlow()

    init {
        // Poblamos con datos quemados de FakeEvents para previsualizar
        _uiState.update { state ->
            state.copy(
                enrolledEvents = listOf(
                    EnrolledEvent(FakeEvents.events[0], EnrollmentStatus.CONFIRMED),
                    EnrolledEvent(FakeEvents.events[1], EnrollmentStatus.CONFIRMED),
                    EnrolledEvent(FakeEvents.events[2], EnrollmentStatus.WAITLIST)
                ),
                createdEvents = listOf(
                    FakeEvents.events[0] // Ejemplo de un evento creado por mí
                )
            )
        }
    }

    fun onTabSelected(tab: MyEventsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun cancelEnrollment(eventId: Int) {
        _uiState.update { state ->
            state.copy(
                enrolledEvents = state.enrolledEvents.filterNot { it.event.id == eventId }
            )
        }
    }
}

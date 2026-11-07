package com.eventoscomunitarios.app.features.my_events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MyEventsTab { ENROLLED, CREATED }
enum class MyEventAction { CANCEL_REGISTRATION, DELETE, FINISH }

data class EnrolledEvent(
    val event: Event,
    val status: EventRegistrationStatus,
    val waitlistPosition: Int? = null
)

data class MyEventsUiState(
    val selectedTab: MyEventsTab = MyEventsTab.ENROLLED,
    val enrolledEvents: List<EnrolledEvent> = emptyList(),
    val createdEvents: List<Event> = emptyList(),
    val waitlistCounts: Map<Int, Int> = emptyMap(),
    val action: MyEventAction? = null,
    val actionEvent: Event? = null,
    val feedback: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MyEventsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(MyEventsUiState())
    val uiState: StateFlow<MyEventsUiState> = _uiState.asStateFlow()

    init {
        updateEvents(events.events.value, events.registrations.value)
        viewModelScope.launch {
            merge(events.events.map { Unit }, events.registrations.map { Unit }, users.users.map { Unit },
                events.syncState.map { Unit }, users.syncState.map { Unit }).collect {
                updateEvents(events.events.value, events.registrations.value)
            }
        }
    }

    fun onTabSelected(tab: MyEventsTab) = _uiState.update { it.copy(selectedTab = tab) }

    fun requestAction(eventId: Int, action: MyEventAction) {
        if (_uiState.value.isSaving) return
        val event = events.findEvent(eventId) ?: return
        _uiState.update { it.copy(action = action, actionEvent = event) }
    }

    fun dismissAction() {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(action = null, actionEvent = null) }
    }
    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }

    fun confirmAction() {
        val state = _uiState.value
        val event = state.actionEvent ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = when (state.action) {
                MyEventAction.CANCEL_REGISTRATION -> events.cancelRegistration(userId, event.id)
                MyEventAction.DELETE -> events.delete(userId, event.id)
                MyEventAction.FINISH -> events.finish(userId, event.id)
                null -> Result.failure(IllegalStateException("Selecciona una acción"))
            }
            val success = when (state.action) {
                MyEventAction.CANCEL_REGISTRATION -> "Inscripción cancelada"
                MyEventAction.DELETE -> "Publicación eliminada"
                else -> "Evento finalizado"
            }
            _uiState.update { it.copy(isSaving = false, feedback = result.exceptionOrNull()?.message ?: success) }
            if (result.isSuccess) dismissAction()
        }
    }

    fun cancelEnrollment(eventId: Int) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = events.cancelRegistration(userId, eventId)
            _uiState.update { it.copy(isSaving = false, feedback = result.exceptionOrNull()?.message ?: "Inscripción cancelada") }
        }
    }

    private fun updateEvents(allEvents: List<Event>, registrations: List<EventRegistration>) {
        val sourceLoading = events.syncState.value.isLoading || users.syncState.value.isLoading
        val sourceError = events.syncState.value.error ?: users.syncState.value.error
        if (sourceLoading) {
            _uiState.update { it.copy(isLoading = true) }
            return
        }
        if (users.findUser(userId)?.accountStatus != AccountStatus.ACTIVE) {
            _uiState.update { it.copy(enrolledEvents = emptyList(), createdEvents = emptyList(), isLoading = false, error = sourceError ?: "No se encontró una cuenta activa") }
            return
        }
        val currentEvents = allEvents.map { event ->
            users.findUser(event.organizerId)?.let { event.copy(organizerName = it.name, submittedByName = it.name) } ?: event
        }
        val byId = currentEvents.associateBy(Event::id)
        val waiting = registrations.filter { it.status == EventRegistrationStatus.WAITLIST }.groupBy { it.eventId }
        val enrolled = registrations.filter { it.userId == userId }.mapNotNull { registration ->
            byId[registration.eventId]?.let { event ->
                val position = if (registration.status == EventRegistrationStatus.WAITLIST) {
                    (waiting[event.id]?.indexOfFirst { it.userId == userId } ?: -1) + 1
                } else null
                EnrolledEvent(event, registration.status, position)
            }
        }
        _uiState.update {
            it.copy(
                enrolledEvents = enrolled,
                createdEvents = currentEvents.filter { event -> event.organizerId == userId },
                waitlistCounts = waiting.mapValues { (_, entries) -> entries.size },
                error = sourceError,
                isLoading = false
            )
        }
    }
}

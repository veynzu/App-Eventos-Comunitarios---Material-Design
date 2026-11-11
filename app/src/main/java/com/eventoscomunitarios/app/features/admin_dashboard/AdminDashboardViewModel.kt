package com.eventoscomunitarios.app.features.admin_dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val loadResult: RequestResult = RequestResult.Loading,
    val activeEvents: Int = 0,
    val pendingEvents: List<Event> = emptyList(),
    val totalUsers: Int = 0,
    val pendingOrganizers: Int = 0,
    val totalAttendance: Int = 0,
    val waitingCount: Int = 0,
    val finishedCount: Int = 0,
    val rejectedCount: Int = 0,
    val categoryCounts: Map<EventCategory, Int> = emptyMap(),
    val actionResult: RequestResult? = null,
    val actionMessage: String? = null,
    val rejectionEvent: Event? = null,
    val rejectionReason: String = "",
    val reasonError: String? = null
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            merge(events.events.map { Unit }, events.registrations.map { Unit }, users.users.map { Unit },
                events.syncState.map { Unit }, users.syncState.map { Unit }).collect { refresh() }
        }
    }

    private fun refresh() {
        val sourceError = events.syncState.value.error ?: users.syncState.value.error
        if (events.syncState.value.isLoading || users.syncState.value.isLoading) {
            _uiState.update { it.copy(loadResult = RequestResult.Loading) }
            return
        }
        if (sourceError != null) {
            _uiState.update { it.copy(loadResult = RequestResult.Failure(sourceError)) }
            return
        }
        val user = users.findUser(userId)
        if (user?.role != UserRole.ADMINISTRATOR || user.accountStatus != AccountStatus.ACTIVE) {
            _uiState.value = AdminDashboardUiState(loadResult = RequestResult.Failure("No tienes permisos de moderador"))
            return
        }
        val allEvents = events.events.value.map { event ->
            users.findUser(event.organizerId)?.let { event.copy(organizerName = it.name, submittedByName = it.name) } ?: event
        }
        val registrations = events.registrations.value
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(
            loadResult = RequestResult.Success,
            activeEvents = allEvents.count { event -> event.moderationStatus == EventModerationStatus.APPROVED && !event.isFinished && event.endsAt > now },
            pendingEvents = allEvents.filter { event -> event.moderationStatus == EventModerationStatus.PENDING },
            totalUsers = users.users.value.size,
            pendingOrganizers = users.users.value.count { user -> user.role == UserRole.ORGANIZER && user.organizerVerificationStatus == com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus.PENDING },
            totalAttendance = registrations.count { entry -> entry.status == EventRegistrationStatus.CONFIRMED },
            waitingCount = registrations.count { entry -> entry.status == EventRegistrationStatus.WAITLIST },
            finishedCount = allEvents.count { event -> event.moderationStatus == EventModerationStatus.APPROVED && (event.isFinished || event.endsAt <= now) },
            rejectedCount = allEvents.count { event -> event.moderationStatus == EventModerationStatus.REJECTED },
            categoryCounts = EventCategory.entries.associateWith { category -> allEvents.count { event -> event.category == category } }
        ) }
    }

    fun approve(eventId: Int) {
        if (_uiState.value.actionResult == RequestResult.Loading) return
        _uiState.update { it.copy(actionResult = RequestResult.Loading) }
        viewModelScope.launch { showResult(events.moderate(userId, eventId, EventModerationStatus.APPROVED), "El evento fue aprobado y ya aparece en el inicio.") }
    }
    fun reject(eventId: Int) {
        if (_uiState.value.actionResult == RequestResult.Loading) return
        val event = events.findEvent(eventId) ?: return
        _uiState.update { it.copy(rejectionEvent = event, rejectionReason = "", reasonError = null) }
    }
    fun onReasonChange(value: String) = _uiState.update { it.copy(rejectionReason = value, reasonError = null) }
    fun dismissRejection() { if (_uiState.value.actionResult != RequestResult.Loading) _uiState.update { it.copy(rejectionEvent = null, rejectionReason = "", reasonError = null) } }

    fun confirmRejection() {
        val state = _uiState.value
        if (state.actionResult == RequestResult.Loading) return
        val event = state.rejectionEvent ?: return
        if (state.rejectionReason.isBlank()) {
            _uiState.update { it.copy(reasonError = "Explica el motivo del rechazo") }
            return
        }
        _uiState.update { it.copy(actionResult = RequestResult.Loading) }
        viewModelScope.launch {
        val result = events.moderate(userId, event.id, EventModerationStatus.REJECTED, state.rejectionReason.trim())
        _uiState.update { it.copy(actionResult = null) }
        if (result.isSuccess) dismissRejection()
        else _uiState.update { it.copy(reasonError = result.exceptionOrNull()?.message) }
        showResult(result, "El evento fue rechazado. El autor recibirá el motivo.")
        }
    }

    fun clearActionResult() = _uiState.update { it.copy(actionResult = null, actionMessage = null) }
    private fun showResult(result: Result<Unit>, message: String) {
        _uiState.update { it.copy(
            actionResult = result.fold(onSuccess = { RequestResult.Success }, onFailure = { error -> RequestResult.Failure(error.message ?: "No se pudo completar la acción") }),
            actionMessage = result.exceptionOrNull()?.message ?: message
        ) }
    }
}

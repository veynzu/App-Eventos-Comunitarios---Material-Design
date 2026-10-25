package com.eventoscomunitarios.app.features.admin_dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val userId: String = "",
    val loadResult: RequestResult = RequestResult.Loading,
    val activeEvents: Int = 0,
    val pendingEvents: List<Event> = emptyList(),
    val totalUsers: Int = 0,
    val totalAttendance: Int = 0,
    val weeklyAttendance: List<Int> = listOf(9, 14, 12, 18, 16, 23),
    val actionResult: RequestResult? = null,
    val actionMessage: String? = null
)

class AdminDashboardViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(AdminDashboardUiState(userId = userId))
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        val admin = FakeUsers.users.firstOrNull { it.id == userId }
        val allowed = admin?.role == UserRole.ADMINISTRATOR && admin.accountStatus == AccountStatus.ACTIVE
        if (!allowed) {
            _uiState.update { it.copy(loadResult = RequestResult.Failure("No tienes permisos de administrador")) }
        } else {
            refresh(FakeEvents.events)
            viewModelScope.launch { FakeEvents.eventsFlow.collect(::refresh) }
        }
    }

    fun approve(eventId: Int) = moderate(eventId, EventModerationStatus.APPROVED, "Evento aprobado correctamente")

    fun reject(eventId: Int) = moderate(eventId, EventModerationStatus.REJECTED, "Evento rechazado")

    fun clearActionResult() {
        _uiState.update { it.copy(actionResult = null, actionMessage = null) }
    }

    private fun moderate(eventId: Int, status: EventModerationStatus, message: String) {
        if (_uiState.value.actionResult == RequestResult.Loading) return
        val event = FakeEvents.events.firstOrNull { it.id == eventId }
        if (event?.moderationStatus != EventModerationStatus.PENDING) {
            _uiState.update { it.copy(actionResult = RequestResult.Failure("El evento ya fue moderado")) }
            return
        }
        _uiState.update { it.copy(actionResult = RequestResult.Loading, actionMessage = null) }
        viewModelScope.launch {
            delay(350)
            FakeEvents.updateModerationStatus(eventId, status)
            _uiState.update { it.copy(actionResult = RequestResult.Success, actionMessage = message) }
        }
    }

    private fun refresh(events: List<Event>) {
        _uiState.update {
            it.copy(
                loadResult = RequestResult.Success,
                activeEvents = events.count { event -> event.moderationStatus == EventModerationStatus.APPROVED },
                pendingEvents = events.filter { event -> event.moderationStatus == EventModerationStatus.PENDING },
                totalUsers = FakeUsers.users.size,
                totalAttendance = events.sumOf(Event::attendeeCount)
            )
        }
    }
}

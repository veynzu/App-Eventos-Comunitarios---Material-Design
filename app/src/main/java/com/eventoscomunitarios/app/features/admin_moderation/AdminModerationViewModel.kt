package com.eventoscomunitarios.app.features.admin_moderation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModerationTab(val label: String, val status: EventModerationStatus) {
    PENDING("Pendientes", EventModerationStatus.PENDING),
    APPROVED("Aprobados", EventModerationStatus.APPROVED),
    REJECTED("Rechazados", EventModerationStatus.REJECTED)
}

data class AdminModerationUiState(
    val userId: String = "",
    val loadResult: RequestResult = RequestResult.Loading,
    val selectedTab: ModerationTab = ModerationTab.PENDING,
    val category: EventCategory? = null,
    val events: List<Event> = emptyList(),
    val feedback: String? = null
) {
    val visibleEvents: List<Event>
        get() = events.filter { it.moderationStatus == selectedTab.status && (category == null || it.category == category) }
    val pendingCount: Int get() = events.count { it.moderationStatus == EventModerationStatus.PENDING }
}

class AdminModerationViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(AdminModerationUiState(userId = userId))
    val uiState: StateFlow<AdminModerationUiState> = _uiState.asStateFlow()

    init {
        val admin = FakeUsers.users.firstOrNull { it.id == userId }
        if (admin?.role != UserRole.ADMINISTRATOR || admin.accountStatus != AccountStatus.ACTIVE) {
            _uiState.update { it.copy(loadResult = RequestResult.Failure("No tienes permisos de administrador")) }
        } else {
            _uiState.update { it.copy(events = FakeEvents.events, loadResult = RequestResult.Success) }
            viewModelScope.launch {
                FakeEvents.eventsFlow.collect { events -> _uiState.update { it.copy(events = events) } }
            }
        }
    }

    fun selectTab(tab: ModerationTab) = _uiState.update { it.copy(selectedTab = tab, feedback = null) }

    fun selectCategory(category: EventCategory?) = _uiState.update {
        it.copy(category = if (it.category == category) null else category, feedback = null)
    }

    fun approve(eventId: Int) = moderate(eventId, EventModerationStatus.APPROVED, "Evento aprobado")

    fun reject(eventId: Int) = moderate(eventId, EventModerationStatus.REJECTED, "Evento rechazado")

    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }

    private fun moderate(eventId: Int, status: EventModerationStatus, feedback: String) {
        val event = FakeEvents.updateModerationStatus(eventId, status)
        _uiState.update {
            it.copy(
                events = FakeEvents.events,
                feedback = if (event != null) feedback else "Evento no encontrado"
            )
        }
    }
}

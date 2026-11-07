package com.eventoscomunitarios.app.features.admin_moderation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModerationTab(val label: String, val status: EventModerationStatus) {
    PENDING("Pendientes", EventModerationStatus.PENDING),
    APPROVED("Aprobados", EventModerationStatus.APPROVED),
    REJECTED("Rechazados", EventModerationStatus.REJECTED)
}
enum class ModerationAction { REJECT, DELETE, FINISH }

data class AdminModerationUiState(
    val userId: String = "",
    val loadResult: RequestResult = RequestResult.Loading,
    val selectedTab: ModerationTab = ModerationTab.PENDING,
    val category: EventCategory? = null,
    val query: String = "",
    val events: List<Event> = emptyList(),
    val feedback: String? = null,
    val isSaving: Boolean = false,
    val action: ModerationAction? = null,
    val actionEvent: Event? = null,
    val rejectionReason: String = "",
    val reasonError: String? = null
) {
    val visibleEvents: List<Event> get() = events.filter {
        it.moderationStatus == selectedTab.status && (category == null || it.category == category) &&
            (query.isBlank() || it.title.contains(query.trim(), true) || it.organizerName.contains(query.trim(), true))
    }
    val pendingCount: Int get() = events.count { it.moderationStatus == EventModerationStatus.PENDING }
}

@HiltViewModel
class AdminModerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(AdminModerationUiState(userId = userId))
    val uiState: StateFlow<AdminModerationUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            combine(events.events, users.users, events.syncState, users.syncState) { _, _, _, _ -> Unit }.collect { refresh() }
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
            _uiState.update { it.copy(events = emptyList(), loadResult = RequestResult.Failure("No tienes permisos de moderador")) }
        } else {
            val currentEvents = events.events.value.map { event ->
                users.findUser(event.organizerId)?.let { event.copy(organizerName = it.name, submittedByName = it.name) } ?: event
            }
            _uiState.update { it.copy(events = currentEvents, loadResult = RequestResult.Success) }
        }
    }

    fun selectTab(tab: ModerationTab) = _uiState.update { it.copy(selectedTab = tab, feedback = null) }
    fun selectCategory(category: EventCategory?) = _uiState.update { it.copy(category = category) }
    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }
    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }

    fun approve(eventId: Int) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch { showResult(events.moderate(userId, eventId, EventModerationStatus.APPROVED), "Evento aprobado") }
    }
    fun reject(eventId: Int) = requestAction(eventId, ModerationAction.REJECT)
    fun requestDelete(eventId: Int) = requestAction(eventId, ModerationAction.DELETE)
    fun requestFinish(eventId: Int) = requestAction(eventId, ModerationAction.FINISH)

    private fun requestAction(eventId: Int, action: ModerationAction) {
        if (_uiState.value.isSaving) return
        val event = events.findEvent(eventId) ?: return
        _uiState.update { it.copy(action = action, actionEvent = event, rejectionReason = "", reasonError = null) }
    }
    fun onReasonChange(value: String) = _uiState.update { it.copy(rejectionReason = value, reasonError = null) }
    fun dismissAction() { if (!_uiState.value.isSaving) _uiState.update { it.copy(action = null, actionEvent = null, rejectionReason = "", reasonError = null) } }

    fun confirmAction() {
        val state = _uiState.value
        if (state.isSaving) return
        val event = state.actionEvent ?: return
        if (state.action == ModerationAction.REJECT && state.rejectionReason.isBlank()) {
            _uiState.update { it.copy(reasonError = "Explica el motivo del rechazo") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
        val result = when (state.action) {
            ModerationAction.REJECT -> events.moderate(userId, event.id, EventModerationStatus.REJECTED, state.rejectionReason.trim())
            ModerationAction.DELETE -> events.delete(userId, event.id)
            ModerationAction.FINISH -> events.finish(userId, event.id)
            null -> Result.failure(IllegalStateException("Selecciona una acción"))
        }
        val message = when (state.action) {
            ModerationAction.REJECT -> "Evento rechazado. El autor recibirá el motivo."
            ModerationAction.DELETE -> "Publicación eliminada"
            else -> "Evento finalizado"
        }
        showResult(result, message)
        if (result.isSuccess) dismissAction()
        else _uiState.update { it.copy(reasonError = result.exceptionOrNull()?.message) }
        }
    }

    private fun showResult(result: Result<Unit>, success: String) {
        _uiState.update { it.copy(isSaving = false, feedback = result.exceptionOrNull()?.message ?: success) }
    }
}

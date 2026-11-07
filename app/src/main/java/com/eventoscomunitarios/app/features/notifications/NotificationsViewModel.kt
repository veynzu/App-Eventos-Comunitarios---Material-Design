package com.eventoscomunitarios.app.features.notifications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.util.toFirebaseMessage
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.AppNotification
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationItem(val notification: AppNotification, val canOpenEvent: Boolean)

data class NotificationsUiState(
    val items: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val openEventId: Int? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val community: CommunityUseCases,
    private val events: EventUseCases,
    private val users: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            merge(
                community.notifications.map { Unit }, events.events.map { Unit }, users.users.map { Unit },
                community.syncState.map { Unit }, events.syncState.map { Unit }, users.syncState.map { Unit }
            ).collect { refresh() }
        }
    }

    fun openNotification(notificationId: Int) {
        if (_uiState.value.items.none { it.notification.id == notificationId }) return
        launchAction {
            community.markRead(userId, notificationId).fold(
                onSuccess = {
                    refresh()
                    val notification = community.notifications.value.firstOrNull { it.id == notificationId && it.userId == userId }
                    val event = notification?.eventId?.let(events::findEvent)
                    val destination = event?.id?.takeIf { events.canView(userId, event) }
                    _uiState.update { it.copy(openEventId = destination) }
                },
                onFailure = { failure -> _uiState.update { it.copy(message = failure.toFirebaseMessage()) } }
            )
        }
    }

    fun clearNavigation() { _uiState.update { it.copy(openEventId = null) } }

    fun markAllRead() {
        launchAction {
            community.markAllRead(userId).fold(
                onSuccess = { refresh(); _uiState.update { it.copy(message = "Todas las notificaciones están leídas") } },
                onFailure = { failure -> _uiState.update { it.copy(message = failure.toFirebaseMessage()) } }
            )
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    private fun launchAction(action: suspend () -> Unit) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _uiState.update { it.copy(message = failure.toFirebaseMessage()) } }
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    private fun refresh() {
        val synchronization = listOf(community.syncState.value, events.syncState.value, users.syncState.value)
        val error = synchronization.firstNotNullOfOrNull { it.error }
        if (synchronization.any { it.isLoading } && error == null) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            return
        }
        if (error != null) {
            _uiState.update { it.copy(isLoading = false, error = error) }
            return
        }
        val user = users.findUser(userId)
        if (user == null || user.accountStatus != AccountStatus.ACTIVE) {
            _uiState.update { it.copy(items = emptyList(), unreadCount = 0, isLoading = false, error = "No se pueden consultar las notificaciones de esta cuenta") }
            return
        }
        val notifications = community.notifications.value.filter { it.userId == userId }
            .sortedWith(compareByDescending<AppNotification> { it.createdAt }.thenByDescending { it.id })
        _uiState.update {
            it.copy(
                items = notifications.map { notification ->
                    val event = notification.eventId?.let(events::findEvent)
                    NotificationItem(notification, event != null && events.canView(userId, event))
                },
                unreadCount = notifications.count { notification -> !notification.isRead },
                isLoading = false, error = null
            )
        }
    }
}

package com.eventoscomunitarios.app.features.admin_users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class UserFilter(val label: String) {
    ALL("Todos"),
    PARTICIPANTS("Clientes"),
    ORGANIZERS("Organizadores")
}

data class AdminUsersUiState(
    val userId: String = "",
    val loadResult: RequestResult = RequestResult.Loading,
    val query: String = "",
    val selectedFilter: UserFilter = UserFilter.ALL,
    val users: List<User> = emptyList(),
    val feedback: String? = null
) {
    val visibleUsers: List<User>
        get() = users.filter { user ->
            user.role != UserRole.ADMINISTRATOR &&
                when (selectedFilter) {
                    UserFilter.ALL -> true
                    UserFilter.PARTICIPANTS -> user.role == UserRole.PARTICIPANT
                    UserFilter.ORGANIZERS -> user.role == UserRole.ORGANIZER
                } && (query.isBlank() || user.name.contains(query, true) || user.email.contains(query, true))
        }
    val suspendedCount: Int get() = users.count { it.accountStatus == AccountStatus.SUSPENDED }
}

class AdminUsersViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(AdminUsersUiState(userId = userId))
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init {
        val admin = FakeUsers.users.firstOrNull { it.id == userId }
        _uiState.update {
            if (admin?.role == UserRole.ADMINISTRATOR && admin.accountStatus == AccountStatus.ACTIVE) {
                it.copy(users = FakeUsers.users.toList(), loadResult = RequestResult.Success)
            } else {
                it.copy(loadResult = RequestResult.Failure("No tienes permisos de administrador"))
            }
        }
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query.take(80)) }

    fun selectFilter(filter: UserFilter) = _uiState.update { it.copy(selectedFilter = filter) }

    fun toggleAccountStatus(targetUserId: String) {
        val user = FakeUsers.users.firstOrNull { it.id == targetUserId } ?: return
        if (user.role == UserRole.ADMINISTRATOR) return
        val status = if (user.accountStatus == AccountStatus.ACTIVE) AccountStatus.SUSPENDED else AccountStatus.ACTIVE
        val updated = FakeUsers.updateAccountStatus(targetUserId, status) ?: return
        _uiState.update { state ->
            state.copy(
                users = FakeUsers.users.toList(),
                feedback = if (updated.accountStatus == AccountStatus.SUSPENDED) "Usuario suspendido" else "Usuario reactivado"
            )
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }
}

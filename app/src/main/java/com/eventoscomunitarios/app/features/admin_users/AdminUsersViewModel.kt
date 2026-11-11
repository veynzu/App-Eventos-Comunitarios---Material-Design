package com.eventoscomunitarios.app.features.admin_users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.OrganizerIdentity
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

enum class UserFilter(val label: String) {
    ALL("Todos"),
    PARTICIPANTS("Clientes"),
    ORGANIZERS("Organizadores"),
    PENDING("Por verificar")
}

data class AdminUsersUiState(
    val userId: String = "",
    val loadResult: RequestResult = RequestResult.Loading,
    val query: String = "",
    val selectedFilter: UserFilter = UserFilter.ALL,
    val users: List<User> = emptyList(),
    val feedback: String? = null,
    val isSaving: Boolean = false,
    val reviewingUser: User? = null,
    val identity: OrganizerIdentity? = null,
    val identityResult: RequestResult? = null
) {
    val visibleUsers: List<User>
        get() = users.filter { user ->
            user.role != UserRole.ADMINISTRATOR &&
                when (selectedFilter) {
                    UserFilter.ALL -> true
                    UserFilter.PARTICIPANTS -> user.role == UserRole.PARTICIPANT
                    UserFilter.ORGANIZERS -> user.role == UserRole.ORGANIZER
                    UserFilter.PENDING -> user.role == UserRole.ORGANIZER && user.organizerVerificationStatus == OrganizerVerificationStatus.PENDING
                } && (query.isBlank() || user.name.contains(query, true) || user.email.contains(query, true))
        }
    val suspendedCount: Int get() = users.count { it.accountStatus == AccountStatus.SUSPENDED }
}

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val userUseCases: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(AdminUsersUiState(userId = userId))
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { merge(userUseCases.users.map { Unit }, userUseCases.syncState.map { Unit }).collect { refresh() } }
    }

    private fun refresh() {
        val admin = userUseCases.findUser(userId)
        val sync = userUseCases.syncState.value
        _uiState.update {
            if (sync.isLoading && admin == null) {
                it.copy(loadResult = RequestResult.Loading)
            } else if (sync.error != null) {
                it.copy(loadResult = RequestResult.Failure(sync.error))
            } else if (admin?.role == UserRole.ADMINISTRATOR && admin.accountStatus == AccountStatus.ACTIVE) {
                it.copy(users = userUseCases.users.value, loadResult = RequestResult.Success)
            } else {
                it.copy(users = emptyList(), loadResult = RequestResult.Failure("No tienes permisos de administrador"))
            }
        }
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query.take(80)) }

    fun selectFilter(filter: UserFilter) = _uiState.update { it.copy(selectedFilter = filter) }

    fun toggleAccountStatus(targetUserId: String) {
        if (_uiState.value.isSaving) return
        val user = userUseCases.findUser(targetUserId) ?: return
        if (user.role == UserRole.ADMINISTRATOR) return
        val status = if (user.accountStatus == AccountStatus.ACTIVE) AccountStatus.SUSPENDED else AccountStatus.ACTIVE
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
        val result = userUseCases.updateAccountStatus(userId, targetUserId, status)
        if (result.isFailure) {
            _uiState.update { it.copy(isSaving = false, feedback = result.exceptionOrNull()?.message) }
            return@launch
        }
        val updated = result.getOrThrow()
        _uiState.update { state ->
            state.copy(
                isSaving = false,
                users = userUseCases.users.value,
                feedback = if (updated.accountStatus == AccountStatus.SUSPENDED) "Usuario suspendido" else "Usuario reactivado"
            )
        }
        }
    }

    fun reviewIdentity(targetUserId: String) {
        if (_uiState.value.isSaving) return
        val target = userUseCases.findUser(targetUserId) ?: return
        _uiState.update { it.copy(reviewingUser = target, identity = null, identityResult = RequestResult.Loading) }
        viewModelScope.launch {
            val result = userUseCases.loadOrganizerIdentity(userId, targetUserId)
            _uiState.update {
                if (it.reviewingUser?.id != targetUserId) it
                else it.copy(identity = result.getOrNull(), identityResult = result.fold(
                    onSuccess = { RequestResult.Success }, onFailure = { RequestResult.Failure(it.message.orEmpty()) }))
            }
        }
    }

    fun dismissIdentity() {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(reviewingUser = null, identity = null, identityResult = null) }
    }

    fun confirmIdentity(approve: Boolean) {
        val state = _uiState.value
        val target = state.reviewingUser ?: return
        val identity = state.identity ?: return
        if (state.isSaving || state.identityResult != RequestResult.Success) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = userUseCases.reviewOrganizerIdentity(userId, target.id, identity.photoPath, approve)
            _uiState.update { it.copy(isSaving = false,
                reviewingUser = if (result.isSuccess) null else it.reviewingUser,
                identity = if (result.isSuccess) null else it.identity,
                feedback = result.exceptionOrNull()?.message ?: if (approve) "Organizador verificado" else "Cuenta bloqueada") }
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }
}

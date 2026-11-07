package com.eventoscomunitarios.app.features.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

data class SessionUiState(
    val userId: String? = null,
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val dataSyncError: String? = null
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val users: UserUseCases,
    private val events: EventUseCases? = null,
    private val community: CommunityUseCases? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            merge(users.currentUserId.map { Unit }, users.sessionReady.map { Unit },
                users.users.map { Unit }, users.syncState.map { Unit },
                events?.syncState?.map { Unit } ?: flowOf(Unit),
                community?.syncState?.map { Unit } ?: flowOf(Unit)
            ).collect { refresh() }
        }
    }

    fun signIn(user: User) {
        savedStateHandle["userId"] = user.id
        _uiState.value = SessionUiState(userId = user.id, user = user, isLoading = false)
    }

    fun signOut() {
        users.signOut()
        savedStateHandle["userId"] = null
        _uiState.value = SessionUiState(isLoading = false)
    }

    fun retry() {
        viewModelScope.launch {
            users.refreshSession()
            refresh()
        }
    }

    private fun refresh() {
        val uid = users.currentUserId.value
        val user = uid?.let(users::findUser)
        val sync = users.syncState.value
        val loading = !users.sessionReady.value || (uid != null && user == null && sync.isLoading)
        if (!loading && user?.accountStatus == AccountStatus.SUSPENDED) {
            signOut()
            return
        }
        // La sesión viene de FirebaseAuth, nunca de una lista de perfiles inicialmente vacía.
        _uiState.value = SessionUiState(uid, user, loading,
            error = if (uid != null && !loading && user == null) sync.error ?: "No se pudo cargar el perfil de esta cuenta." else null,
            dataSyncError = if (uid == null) null else sync.error ?: events?.syncState?.value?.error ?: community?.syncState?.value?.error)
        savedStateHandle["userId"] = uid
    }
}

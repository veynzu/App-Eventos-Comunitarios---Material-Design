package com.eventoscomunitarios.app.features.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionUiState(
    val userId: String? = null
)

class SessionViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SessionUiState(userId = savedStateHandle["userId"])
    )
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    fun signIn(user: User) {
        savedStateHandle["userId"] = user.id
        _uiState.value = SessionUiState(userId = user.id)
    }

    fun signOut() {
        savedStateHandle["userId"] = null
        _uiState.value = SessionUiState()
    }
}

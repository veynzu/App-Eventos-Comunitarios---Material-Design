package com.eventoscomunitarios.app.features.profile

import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.features.auth.validationErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileUiState(
    val user: User? = null,
    val errors: Map<String, String> = emptyMap(),
    val loadResult: RequestResult = RequestResult.Loading
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadUser(userId: String) {
        _uiState.value = ProfileUiState()
        val user = FakeUsers.users.firstOrNull { it.id == userId }
        val errors = user?.validationErrors() ?: mapOf("user" to "No se encontró el usuario de esta sesión")
        _uiState.value = ProfileUiState(
            user = user.takeIf { errors.isEmpty() },
            errors = errors,
            loadResult = if (errors.isEmpty()) RequestResult.Success
                else RequestResult.Failure("No se puede cargar el perfil")
        )
    }
}

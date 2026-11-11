package com.eventoscomunitarios.app.features.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val recordar: Boolean = true,
    val emailError: String? = null,
    val passwordError: String? = null,
    val loginResult: RequestResult? = null,
    val authenticatedUser: User? = null
) {
    val isFormValid: Boolean
        get() = email.isNotBlank() &&
                password.isNotBlank() &&
                emailError == null &&
                passwordError == null
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val users: UserUseCases) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.update { state ->
            state.copy(
                email = newEmail,
                emailError = validateEmail(newEmail),
                authenticatedUser = null
            )
        }
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update { state ->
            state.copy(
                password = newPassword,
                passwordError = validatePassword(newPassword),
                authenticatedUser = null
            )
        }
    }

    fun onRecordarChange(recordar: Boolean) {
        _uiState.update { state ->
            state.copy(recordar = recordar)
        }
    }

    fun login() {
        if (_uiState.value.loginResult == RequestResult.Loading) return
        _uiState.update { state ->
            state.copy(
                emailError = validateEmail(state.email),
                passwordError = validatePassword(state.password)
            )
        }
        val currentState = _uiState.value
        if (!currentState.isFormValid) return

        _uiState.update { state ->
            state.copy(
                loginResult = RequestResult.Loading,
                authenticatedUser = null
            )
        }

        viewModelScope.launch {
            val result = users.login(currentState.email, currentState.password)
            _uiState.update { state ->
                state.copy(
                    loginResult = result.fold(
                        onSuccess = { RequestResult.Success },
                        onFailure = { RequestResult.Failure(it.message ?: "No se pudo ingresar") }
                    ),
                    authenticatedUser = result.getOrNull()
                )
            }
        }
    }

    fun clearLoginResult() {
        _uiState.update { state ->
            state.copy(loginResult = null)
        }
    }

    fun resetForm() {
        _uiState.value = LoginUiState()
    }

    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "El correo es obligatorio"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                "Ingresa un correo válido"
            else -> null
        }
    }

    private fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "La contraseña es obligatoria"
            else -> null
        }
    }
}

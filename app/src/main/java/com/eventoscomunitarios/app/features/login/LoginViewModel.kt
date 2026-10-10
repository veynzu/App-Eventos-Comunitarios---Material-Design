package com.eventoscomunitarios.app.features.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeUsers
import com.eventoscomunitarios.app.core.util.RequestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val recordar: Boolean = true,
    val emailError: String? = null,
    val passwordError: String? = null,
    val loginResult: RequestResult? = null
) {
    val isFormValid: Boolean
        get() = email.isNotBlank() &&
                password.isNotBlank() &&
                emailError == null &&
                passwordError == null
}

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.update { state ->
            state.copy(
                email = newEmail,
                emailError = validateEmail(newEmail)
            )
        }
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update { state ->
            state.copy(
                password = newPassword,
                passwordError = validatePassword(newPassword)
            )
        }
    }

    fun onRecordarChange(recordar: Boolean) {
        _uiState.update { state ->
            state.copy(recordar = recordar)
        }
    }

    fun login() {
        val currentState = _uiState.value

        if (!currentState.isFormValid) return

        _uiState.update { state ->
            state.copy(loginResult = RequestResult.Loading)
        }

        val credentialsAreValid = FakeUsers.users.any { user ->
            user.email == currentState.email &&
                    user.password == currentState.password
        }

        _uiState.update { state ->
            state.copy(
                loginResult = if (credentialsAreValid) {
                    RequestResult.Success
                } else {
                    RequestResult.Failure("Correo o contraseña incorrectos")
                }
            )
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
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                "Ingresa un correo válido"
            else -> null
        }
    }

    private fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "La contraseña es obligatoria"
            password.length < 8 ->
                "La contraseña debe tener al menos 8 caracteres"
            else -> null
        }
    }
}

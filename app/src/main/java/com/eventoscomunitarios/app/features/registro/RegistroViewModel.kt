package com.eventoscomunitarios.app.features.registro

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RegistroUiState(
    val nombre: String = "",
    val email: String = "",
    val telefono: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isOrganizer: Boolean = false,
    val terminos: Boolean = false,
    val nombreError: String? = null,
    val emailError: String? = null,
    val telefonoError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val registroResult: RequestResult? = null
) {
    val isFormValid: Boolean
        get() = nombre.isNotBlank() &&
                email.isNotBlank() &&
                telefono.isNotBlank() &&
                password.isNotBlank() &&
                confirmPassword.isNotBlank() &&
                terminos &&
                nombreError == null &&
                emailError == null &&
                telefonoError == null &&
                passwordError == null &&
                confirmPasswordError == null
}

class RegistroViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroUiState())
    val uiState: StateFlow<RegistroUiState> = _uiState.asStateFlow()

    fun onNombreChange(newNombre: String) {
        _uiState.update { state ->
            state.copy(
                nombre = newNombre,
                nombreError = validateNombre(newNombre)
            )
        }
    }

    fun onEmailChange(newEmail: String) {
        _uiState.update { state ->
            state.copy(
                email = newEmail,
                emailError = validateEmail(newEmail)
            )
        }
    }

    fun onTelefonoChange(newTelefono: String) {
        _uiState.update { state ->
            state.copy(
                telefono = newTelefono,
                telefonoError = validateTelefono(newTelefono)
            )
        }
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update { state ->
            state.copy(
                password = newPassword,
                passwordError = validatePassword(newPassword),
                confirmPasswordError = validateConfirmPassword(
                    newPassword,
                    state.confirmPassword
                )
            )
        }
    }

    fun onConfirmPasswordChange(newConfirmPassword: String) {
        _uiState.update { state ->
            state.copy(
                confirmPassword = newConfirmPassword,
                confirmPasswordError = validateConfirmPassword(
                    state.password,
                    newConfirmPassword
                )
            )
        }
    }

    fun onAccountTypeChange(isOrganizer: Boolean) {
        _uiState.update { state ->
            state.copy(isOrganizer = isOrganizer)
        }
    }

    fun onTerminosChange(terminos: Boolean) {
        _uiState.update { state ->
            state.copy(terminos = terminos)
        }
    }

    fun registrar() {
        val currentState = _uiState.value

        if (!currentState.isFormValid) return

        _uiState.update { state ->
            state.copy(registroResult = RequestResult.Loading)
        }

        _uiState.update { state ->
            state.copy(registroResult = RequestResult.Success)
        }
    }

    fun clearRegistroResult() {
        _uiState.update { state ->
            state.copy(registroResult = null)
        }
    }

    private fun validateNombre(nombre: String): String? {
        return if (nombre.isBlank()) {
            "El nombre es obligatorio"
        } else {
            null
        }
    }

    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "El correo es obligatorio"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                "Ingresa un correo válido"
            else -> null
        }
    }

    private fun validateTelefono(telefono: String): String? {
        return if (telefono.isBlank()) {
            "El teléfono es obligatorio"
        } else {
            null
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

    private fun validateConfirmPassword(
        password: String,
        confirmPassword: String
    ): String? {
        return when {
            confirmPassword.isBlank() -> "Confirma tu contraseña"
            password != confirmPassword -> "Las contraseñas no coinciden"
            else -> null
        }
    }
}
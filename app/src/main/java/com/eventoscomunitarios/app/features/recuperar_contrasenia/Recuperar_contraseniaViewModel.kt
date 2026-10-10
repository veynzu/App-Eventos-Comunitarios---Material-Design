package com.eventoscomunitarios.app.features.recuperar_contrasenia

import android.util.Patterns
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecuperarContrasenaUiState(
    val email: String = "",
    val emailError: String? = null,
    val solicitudValidada: Boolean = false
) {
    val isEmailValid: Boolean
        get() = email.isNotBlank() && emailError == null
}

class RecuperarContrasenaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecuperarContrasenaUiState())
    val uiState: StateFlow<RecuperarContrasenaUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.update { state ->
            state.copy(
                email = newEmail,
                emailError = if (newEmail.isBlank()) {
                    null
                } else {
                    validateEmail(newEmail)
                },
                solicitudValidada = false
            )
        }
    }

    fun solicitarRecuperacion() {
        val currentState = _uiState.value
        val emailError = validateEmail(currentState.email)

        if (emailError != null) {
            _uiState.update { state ->
                state.copy(
                    emailError = emailError,
                    solicitudValidada = false
                )
            }
            return
        }


        // Por ahora solo se valida el formulario; todavía no se envía ningún email.
        _uiState.update { state ->
            state.copy(
                emailError = null,
                solicitudValidada = true
            )
        }
    }

    fun clearSolicitudValidada() {
        _uiState.update { state ->
            state.copy(solicitudValidada = false)
        }
    }

    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "El correo electrónico es obligatorio"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                "Ingresa un correo electrónico válido"
            else -> null
        }
    }
}

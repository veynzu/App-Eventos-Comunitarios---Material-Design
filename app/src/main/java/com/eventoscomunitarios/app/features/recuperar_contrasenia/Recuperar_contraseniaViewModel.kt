package com.eventoscomunitarios.app.features.recuperar_contrasenia

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecuperarContrasenaUiState(
    val email: String = "",
    val emailError: String? = null,
    val solicitudValidada: Boolean = false,
    val isLoading: Boolean = false
) {
    val isEmailValid: Boolean
        get() = email.isNotBlank() && emailError == null
}

@HiltViewModel
class RecuperarContrasenaViewModel @Inject constructor(private val users: UserUseCases) : ViewModel() {

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
        if (currentState.isLoading) return
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


        _uiState.update { it.copy(isLoading = true, solicitudValidada = false) }
        viewModelScope.launch {
            val result = users.recoverPassword(currentState.email)
            _uiState.update { state ->
                state.copy(isLoading = false, emailError = result.exceptionOrNull()?.message,
                    solicitudValidada = result.isSuccess)
            }
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

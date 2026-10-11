package com.eventoscomunitarios.app.features.registro

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Date
import java.util.UUID

data class RegistroUiState(
    val nombre: String = "",
    val email: String = "",
    val telefono: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isOrganizer: Boolean = false,
    val terminos: Boolean = false,
    val currentStep: Int = 1,
    val documentFrontUri: String? = null,
    val documentBackUri: String? = null,
    val nombreError: String? = null,
    val emailError: String? = null,
    val telefonoError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val documentError: String? = null,
    val terminosError: String? = null,
    val registroResult: RequestResult? = null,
    val registeredUser: User? = null
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

    val documentsAreSelected: Boolean
        get() = documentFrontUri != null && documentBackUri != null
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
            state.copy(
                isOrganizer = isOrganizer,
                currentStep = 1,
                documentFrontUri = null,
                documentBackUri = null,
                documentError = null
            )
        }
    }

    fun onTerminosChange(terminos: Boolean) {
        _uiState.update { state ->
            state.copy(terminos = terminos, terminosError = null)
        }
    }

    fun onDocumentFrontSelected(uri: String?) {
        _uiState.update { state ->
            state.copy(
                documentFrontUri = uri,
                documentError = null
            )
        }
    }

    fun onDocumentBackSelected(uri: String?) {
        _uiState.update { state ->
            state.copy(
                documentBackUri = uri,
                documentError = null
            )
        }
    }

    fun onBackFromDocuments() {
        _uiState.update { state ->
            state.copy(
                currentStep = 1,
                documentError = null
            )
        }
    }

    fun onContinuarClick() {
        if (_uiState.value.registroResult == RequestResult.Loading) return
        validateForm()
        val currentState = _uiState.value
        if (!currentState.isFormValid) return

        if (currentState.isOrganizer && currentState.currentStep == 1) {
            _uiState.update { state ->
                state.copy(currentStep = 2)
            }
            return
        }

        registrar()
    }

    fun registrar() {
        if (_uiState.value.registroResult == RequestResult.Loading) return
        validateForm()
        val currentState = _uiState.value

        if (!currentState.isFormValid) return

        if (currentState.isOrganizer && !currentState.documentsAreSelected) {
            _uiState.update { state ->
                state.copy(
                    documentError = "Selecciona el frente y el reverso de tu documento."
                )
            }
            return
        }

        _uiState.update { it.copy(registroResult = RequestResult.Loading, registeredUser = null) }
        val email = currentState.email.trim()
        if (FakeUsers.users.any { it.email.equals(email, ignoreCase = true) }) {
            val message = "Ya existe una cuenta con ese correo electrónico."
            _uiState.update {
                it.copy(emailError = message, registroResult = RequestResult.Failure(message))
            }
            return
        }

        val user = User(
            id = UUID.randomUUID().toString(),
            name = currentState.nombre.trim(),
            email = email,
            phoneNumber = currentState.telefono.trim(),
            role = if (currentState.isOrganizer) {
                UserRole.ORGANIZER
            } else {
                UserRole.PARTICIPANT
            },
            registrationDate = Date(),
            profilePictureUrl = null
        )

        // Datos temporales compartidos; todavía no se utiliza una base de datos.
        FakeUsers.users.add(user)
        FakeUsers.passwords[user.id] = currentState.password

        _uiState.update { state ->
            state.copy(
                registroResult = RequestResult.Success,
                registeredUser = user
            )
        }
    }

    fun clearRegistroResult() {
        _uiState.update { state ->
            state.copy(registroResult = null)
        }
    }

    fun resetForm() {
        _uiState.value = RegistroUiState()
    }

    private fun validateForm() {
        _uiState.update { state ->
            state.copy(
                nombreError = validateNombre(state.nombre),
                emailError = validateEmail(state.email),
                telefonoError = validateTelefono(state.telefono),
                passwordError = validatePassword(state.password),
                confirmPasswordError = validateConfirmPassword(state.password, state.confirmPassword),
                terminosError = if (state.terminos) null else "Debes aceptar los términos y condiciones"
            )
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
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                "Ingresa un correo válido"
            else -> null
        }
    }

    private fun validateTelefono(telefono: String): String? {
        return when {
            telefono.isBlank() -> "El teléfono es obligatorio"
            !Patterns.PHONE.matcher(telefono.trim()).matches() ||
                telefono.count { it.isDigit() } !in 7..15 -> "Ingresa un teléfono válido"
            else -> null
        }
    }

    private fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "La contraseña es obligatoria"
            password.length < 8 ->
                "La contraseña debe tener al menos 8 caracteres"
            password.none { it.isUpperCase() } ->
                "La contraseña debe incluir al menos una mayúscula"
            password.none { it.isDigit() } ->
                "La contraseña debe incluir al menos un número"
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

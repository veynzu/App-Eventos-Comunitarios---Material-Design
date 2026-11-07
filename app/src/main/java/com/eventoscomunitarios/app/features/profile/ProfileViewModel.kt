package com.eventoscomunitarios.app.features.profile

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserStatistics
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val errors: Map<String, String> = emptyMap(),
    val loadResult: RequestResult = RequestResult.Loading,
    val statistics: UserStatistics? = null,
    val unreadNotifications: Int = 0,
    val isEditing: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val selectedPhotoUri: String? = null,
    val removePhoto: Boolean = false,
    val reauthPassword: String = "",
    val deletePassword: String = "",
    val isBusy: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val accountDeleted: Boolean = false,
    val message: String? = null,
    val syncError: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserUseCases,
    private val events: EventUseCases,
    private val community: CommunityUseCases
) : ViewModel() {
    private var userId: String = ""
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            merge(users.users.map { Unit }, users.syncState.map { Unit }, events.events.map { Unit },
                events.registrations.map { Unit }, community.comments.map { Unit },
                community.votes.map { Unit }, community.notifications.map { Unit },
                events.syncState.map { Unit }, community.syncState.map { Unit }
            ).collect { if (userId.isNotBlank()) refresh() }
        }
    }

    fun loadUser(userId: String) {
        if (this.userId != userId) _uiState.value = ProfileUiState()
        this.userId = userId
        refresh()
    }

    fun startEditing() {
        if (_uiState.value.isBusy) return
        val user = _uiState.value.user ?: return
        _uiState.update { it.copy(isEditing = true, name = user.name, email = user.email,
            phoneNumber = user.phoneNumber.orEmpty(), errors = emptyMap(), selectedPhotoUri = null,
            removePhoto = false, reauthPassword = "") }
    }

    fun cancelEditing() { if (!_uiState.value.isBusy) _uiState.update { it.copy(isEditing = false, errors = emptyMap(), selectedPhotoUri = null, reauthPassword = "", removePhoto = false) } }
    fun onNameChange(value: String) { _uiState.update { it.copy(name = value, errors = it.errors - "name") } }
    fun onEmailChange(value: String) { _uiState.update { it.copy(email = value, errors = it.errors - "email") } }
    fun onPhoneChange(value: String) { _uiState.update { it.copy(phoneNumber = value, errors = it.errors - "phone") } }
    fun onReauthPasswordChange(value: String) { _uiState.update { it.copy(reauthPassword = value, errors = it.errors - "password") } }
    fun onDeletePasswordChange(value: String) { _uiState.update { it.copy(deletePassword = value, errors = it.errors - "deletePassword") } }
    fun onPhotoSelected(uri: String?) { if (uri != null && !_uiState.value.isBusy) _uiState.update { it.copy(selectedPhotoUri = uri, removePhoto = false) } }
    fun removePhoto() { if (!_uiState.value.isBusy) _uiState.update { it.copy(selectedPhotoUri = null, removePhoto = true) } }

    fun saveProfile() {
        val state = _uiState.value
        if (state.isBusy) return
        val changeEmail = !state.email.trim().equals(state.user?.email, true)
        val errors = buildMap {
            if (state.name.trim().length < 3) put("name", "Ingresa un nombre de al menos 3 caracteres")
            if (!Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()) put("email", "Ingresa un correo válido")
            if (state.phoneNumber.isNotBlank() && (!Patterns.PHONE.matcher(state.phoneNumber.trim()).matches() || state.phoneNumber.count(Char::isDigit) !in 7..15))
                put("phone", "Ingresa un teléfono válido de 7 a 15 dígitos")
            if (changeEmail && state.reauthPassword.isBlank()) put("password", "Ingresa tu contraseña actual para cambiar el correo")
        }
        if (errors.isNotEmpty()) { _uiState.update { it.copy(errors = errors) }; return }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            users.updateProfile(userId, state.name, state.email, state.phoneNumber,
                state.selectedPhotoUri, state.reauthPassword, state.removePhoto).fold(
                onSuccess = {
                    _uiState.update { current -> current.copy(isBusy = false, isEditing = false, errors = emptyMap(),
                        reauthPassword = "", selectedPhotoUri = null, removePhoto = false,
                        message = if (changeEmail) "Perfil guardado. Confirma el nuevo correo desde el enlace enviado; luego pulsa Actualizar cuenta." else "Perfil actualizado") }
                    refresh()
                },
                onFailure = { failure -> _uiState.update { it.copy(isBusy = false, reauthPassword = "", message = failure.message ?: "No se pudo actualizar el perfil") } }
            )
        }
    }

    fun refreshAccount() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = users.refreshSession()
            refresh()
            _uiState.update { it.copy(isBusy = false, message = result.exceptionOrNull()?.message ?: "Cuenta actualizada") }
        }
    }

    fun requestDeleteAccount() { if (!_uiState.value.isBusy) _uiState.update { it.copy(showDeleteConfirmation = true, deletePassword = "", errors = emptyMap()) } }
    fun dismissDeleteAccount() { if (!_uiState.value.isBusy) _uiState.update { it.copy(showDeleteConfirmation = false, deletePassword = "") } }
    fun deleteAccount() {
        val state = _uiState.value
        if (state.isBusy) return
        if (state.deletePassword.isBlank()) { _uiState.update { it.copy(errors = it.errors + ("deletePassword" to "Confirma tu contraseña actual")) }; return }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            users.deleteAccount(userId, state.deletePassword).fold(
                onSuccess = { _uiState.update { it.copy(user = null, isBusy = false, showDeleteConfirmation = false, accountDeleted = true, deletePassword = "") } },
                onFailure = { failure -> _uiState.update { it.copy(isBusy = false, deletePassword = "", errors = it.errors + ("deletePassword" to (failure.message ?: "No se pudo eliminar la cuenta"))) } }
            )
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    private fun refresh() {
        val user = users.findUser(userId)
        val sync = users.syncState.value
        _uiState.update {
            it.copy(user = user,
                loadResult = when { user != null -> RequestResult.Success; sync.isLoading -> RequestResult.Loading; else -> RequestResult.Failure(sync.error ?: "No se encontró el perfil") },
                syncError = sync.error ?: events.syncState.value.error ?: community.syncState.value.error,
                statistics = if (user != null) users.statistics(userId) else null,
                unreadNotifications = community.notifications.value.count { notification -> notification.userId == userId && !notification.isRead },
                name = if (it.isEditing) it.name else user?.name.orEmpty(),
                email = if (it.isEditing) it.email else user?.email.orEmpty(),
                phoneNumber = if (it.isEditing) it.phoneNumber else user?.phoneNumber.orEmpty())
        }
    }
}

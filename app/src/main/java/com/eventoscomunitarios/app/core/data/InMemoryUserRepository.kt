package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.RepositorySyncState
import com.eventoscomunitarios.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryUserRepository @Inject constructor() : UserRepository {
    private val mutableUsers = MutableStateFlow(demoUsers())
    private val identityPhotos = mutableMapOf<String, String>()
    private val passwords = demoPasswords().toMutableMap()
    override val users = mutableUsers.asStateFlow()
    override val syncState = MutableStateFlow(RepositorySyncState()).asStateFlow()
    private val mutableCurrentUserId = MutableStateFlow<String?>(null)
    override val currentUserId = mutableCurrentUserId.asStateFlow()
    override val sessionReady = MutableStateFlow(true).asStateFlow()

    override suspend fun save(user: User) {
        mutableUsers.value = if (users.value.any { it.id == user.id }) {
            users.value.map { if (it.id == user.id) user else it }
        } else users.value + user
    }

    override suspend fun delete(userId: String) {
        mutableUsers.value = users.value.filterNot { it.id == userId }
        passwords.remove(userId)
        identityPhotos.remove(userId)
        if (currentUserId.value == userId) signOut()
    }

    override suspend fun login(email: String, password: String): User {
        val user = requireNotNull(users.value.firstOrNull { it.email.equals(email.trim(), true) && passwords[it.id] == password }) { "Correo o contraseña incorrectos" }
        require(user.accountStatus == AccountStatus.ACTIVE) { "La cuenta está suspendida" }
        mutableCurrentUserId.value = user.id
        return user
    }

    override suspend fun register(user: User, password: String, identityPhotoUri: String?): User {
        require(user.role != UserRole.ADMINISTRATOR) { "No es posible registrar administradores desde este formulario" }
        require(user.role != UserRole.ORGANIZER || !identityPhotoUri.isNullOrBlank()) { "Adjunta una foto de tu cédula" }
        if (identityPhotoUri != null) identityPhotos[user.id] = identityPhotoUri
        require(users.value.none { it.email.equals(user.email, true) }) { "Ya existe una cuenta con este correo" }
        val created = user.copy(accountStatus = AccountStatus.ACTIVE,
            organizerVerificationStatus = if (user.role == UserRole.ORGANIZER) OrganizerVerificationStatus.PENDING
                else OrganizerVerificationStatus.NOT_REQUIRED)
        save(created)
        passwords[user.id] = password
        mutableCurrentUserId.value = user.id
        return created
    }

    override suspend fun organizerIdentityPath(userId: String): String? = identityPhotos[userId]
    override suspend fun saveOrganizerIdentity(userId: String, photoPath: String): User {
        val user = requireNotNull(users.value.firstOrNull { it.id == userId })
        require(user.role == UserRole.ORGANIZER && user.accountStatus == AccountStatus.ACTIVE &&
            user.organizerVerificationStatus != OrganizerVerificationStatus.VERIFIED)
        identityPhotos[userId] = photoPath
        return user.copy(organizerVerificationStatus = OrganizerVerificationStatus.PENDING).also { save(it) }
    }
    override suspend fun reviewOrganizerIdentity(userId: String, expectedPhotoPath: String, approve: Boolean): User {
        val user = requireNotNull(users.value.firstOrNull { it.id == userId })
        require(user.role == UserRole.ORGANIZER && user.organizerVerificationStatus == OrganizerVerificationStatus.PENDING)
        require(identityPhotos[userId] == expectedPhotoPath) { "El documento cambió; vuelve a abrir la revisión" }
        return user.copy(organizerVerificationStatus = if (approve) OrganizerVerificationStatus.VERIFIED else OrganizerVerificationStatus.REJECTED,
            accountStatus = if (approve) AccountStatus.ACTIVE else AccountStatus.SUSPENDED).also { save(it) }
    }

    override suspend fun recoverPassword(email: String) { /* Doble de prueba; no envía mensajes. */ }
    override suspend fun reauthenticate(userId: String, password: String) {
        require(password.isEmpty() || passwords[userId] == password) { "La contraseña actual es incorrecta" }
    }
    override suspend fun requestEmailChange(userId: String, newEmail: String) {
        require(users.value.none { it.id != userId && it.email.equals(newEmail, true) }) { "Ya existe una cuenta con este correo" }
    }
    override suspend fun refreshSession(): User? = users.value.firstOrNull { it.id == currentUserId.value }
    override fun signOut() { mutableCurrentUserId.value = null }
}

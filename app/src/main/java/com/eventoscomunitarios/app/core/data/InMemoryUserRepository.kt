package com.eventoscomunitarios.app.core.data

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
        if (currentUserId.value == userId) signOut()
    }

    override suspend fun login(email: String, password: String): User {
        val user = requireNotNull(users.value.firstOrNull { it.email.equals(email.trim(), true) && passwords[it.id] == password }) { "Correo o contraseña incorrectos" }
        require(user.accountStatus == AccountStatus.ACTIVE) { "La cuenta está suspendida" }
        mutableCurrentUserId.value = user.id
        return user
    }

    override suspend fun register(user: User, password: String): User {
        require(users.value.none { it.email.equals(user.email, true) }) { "Ya existe una cuenta con este correo" }
        save(user)
        passwords[user.id] = password
        mutableCurrentUserId.value = user.id
        return user
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

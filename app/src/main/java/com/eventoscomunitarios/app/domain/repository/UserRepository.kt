package com.eventoscomunitarios.app.domain.repository

import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.RepositorySyncState
import kotlinx.coroutines.flow.StateFlow

interface UserRepository {
    val users: StateFlow<List<User>>
    val syncState: StateFlow<RepositorySyncState>
    val currentUserId: StateFlow<String?>
    val sessionReady: StateFlow<Boolean>
    suspend fun login(email: String, password: String): User
    suspend fun register(user: User, password: String): User
    suspend fun recoverPassword(email: String)
    suspend fun save(user: User)
    suspend fun reauthenticate(userId: String, password: String)
    suspend fun requestEmailChange(userId: String, newEmail: String)
    suspend fun refreshSession(): User?
    suspend fun delete(userId: String)
    fun signOut()
}

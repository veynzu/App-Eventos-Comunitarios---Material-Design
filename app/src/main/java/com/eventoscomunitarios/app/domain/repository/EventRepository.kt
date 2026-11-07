package com.eventoscomunitarios.app.domain.repository

import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventChange
import com.eventoscomunitarios.app.domain.model.EventRecord
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.RepositorySyncState
import com.eventoscomunitarios.app.domain.model.User
import kotlinx.coroutines.flow.StateFlow

interface EventRepository {
    val events: StateFlow<List<Event>>
    val registrations: StateFlow<List<EventRegistration>>
    val syncState: StateFlow<RepositorySyncState>
    fun reportCleanupError(message: String)
    suspend fun nextId(): Int
    suspend fun create(actor: User, event: Event): Event
    /** transform se ejecuta sobre datos actuales y puede repetirse si existe concurrencia. */
    suspend fun mutate(
        eventId: Int,
        actor: User,
        activeUserIds: Set<String>,
        transform: (EventRecord, User, Set<String>) -> EventRecord?
    ): EventChange
    suspend fun purge(eventId: Int, actor: User)
    suspend fun ownedBy(userId: String): List<Event>
    suspend fun registeredBy(userId: String): List<Event>
}

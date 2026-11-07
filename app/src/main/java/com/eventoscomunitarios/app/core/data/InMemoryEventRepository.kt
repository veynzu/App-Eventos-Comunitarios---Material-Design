package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventChange
import com.eventoscomunitarios.app.domain.model.EventRecord
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.RepositorySyncState
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Repositorio aislado para pruebas; producción usa FirebaseEventRepository. */
class InMemoryEventRepository : EventRepository {
    private val mutableEvents = MutableStateFlow(demoEvents())
    private val mutableRegistrations = MutableStateFlow(demoRegistrations().mapIndexed { index, entry -> entry.copy(queueOrder = index.toLong() + 1) })
    private val mutex = Mutex()
    override val events = mutableEvents.asStateFlow()
    override val registrations = mutableRegistrations.asStateFlow()
    private val mutableSync = MutableStateFlow(RepositorySyncState())
    override val syncState = mutableSync.asStateFlow()
    override fun reportCleanupError(message: String) { mutableSync.value = RepositorySyncState(error = message) }
    private var nextEventId = (events.value.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun nextId(): Int = mutex.withLock { nextEventId++ }
    override suspend fun create(actor: User, event: Event): Event = mutex.withLock {
        require(events.value.none { it.id == event.id }) { "El evento ya existe" }
        putEvent(event)
        event
    }
    override suspend fun mutate(eventId: Int, actor: User, activeUserIds: Set<String>, transform: (EventRecord, User, Set<String>) -> EventRecord?): EventChange = mutex.withLock {
        val current = requireNotNull(events.value.firstOrNull { it.id == eventId }) { "Publicación no encontrada" }
        val before = EventRecord(current, registrations.value.filter { it.eventId == eventId })
        val after = transform(before, actor, activeUserIds)
        if (after == null) mutableEvents.value = events.value.filterNot { it.id == eventId }
        else putEvent(after.event)
        mutableRegistrations.value = (registrations.value.filterNot { it.eventId == eventId } + after?.registrations.orEmpty()).sortedBy { it.queueOrder }
        EventChange(before, after)
    }
    override suspend fun purge(eventId: Int, actor: User) = Unit
    override suspend fun ownedBy(userId: String): List<Event> = events.value.filter { it.organizerId == userId }
    override suspend fun registeredBy(userId: String): List<Event> {
        val ids = registrations.value.filter { it.userId == userId }.map { it.eventId }.toSet()
        return events.value.filter { it.id in ids }
    }

    // Preparación de escenarios de prueba, sin exponer escrituras ciegas en la interfaz productiva.
    suspend fun save(event: Event) = mutex.withLock { putEvent(event) }
    suspend fun saveRegistration(registration: EventRegistration) = mutex.withLock {
        mutableRegistrations.value = registrations.value.filterNot { it.userId == registration.userId && it.eventId == registration.eventId } + registration
    }
    suspend fun deleteRegistration(userId: String, eventId: Int) = mutex.withLock {
        mutableRegistrations.value = registrations.value.filterNot { it.userId == userId && it.eventId == eventId }
    }
    suspend fun delete(eventId: Int) = mutex.withLock {
        mutableEvents.value = events.value.filterNot { it.id == eventId }
        mutableRegistrations.value = registrations.value.filterNot { it.eventId == eventId }
    }
    private fun putEvent(event: Event) {
        mutableEvents.value = if (events.value.any { it.id == event.id }) events.value.map { if (it.id == event.id) event else it }
        else events.value + event
    }
}

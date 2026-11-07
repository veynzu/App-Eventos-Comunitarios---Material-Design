package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.core.util.firebaseResult
import com.eventoscomunitarios.app.core.util.isRemoteImageUri
import com.eventoscomunitarios.app.core.util.toFirebaseMessage
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.eventoscomunitarios.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Reglas de eventos; las decisiones de cupo se ejecutan con el estado actual en una transacción. */
class EventUseCases @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val communityRepository: CommunityRepository,
    private val mediaRepository: MediaRepository? = null
) {
    val events = eventRepository.events
    val registrations = eventRepository.registrations
    val syncState = eventRepository.syncState

    fun findEvent(id: Int): Event? = events.value.firstOrNull { it.id == id && !it.isDeleted }
    fun canPublish(userId: String): Boolean = activeUser(userId) != null
    fun canManage(userId: String, event: Event): Boolean = activeUser(userId)?.let { manages(it, event) } == true
    fun canView(userId: String, event: Event): Boolean = !event.isDeleted && activeUser(userId) != null &&
        (event.moderationStatus == EventModerationStatus.APPROVED || canManage(userId, event) ||
            (event.moderationStatus == EventModerationStatus.PENDING && registrations.value.any { it.userId == userId && it.eventId == event.id }))
    fun canRegister(userId: String, event: Event): Boolean = !event.isDeleted && activeUser(userId) != null &&
        event.organizerId != userId && event.moderationStatus == EventModerationStatus.APPROVED &&
        !event.isFinished && event.startsAt > System.currentTimeMillis()

    suspend fun create(userId: String, event: Event): Result<Event> = firebaseResult {
        val author = requireActiveUser(userId)
        validate(event)
        val id = eventRepository.nextId()
        val photo = uploadPhoto(userId, event.photoUri)
        val created = try {
            eventRepository.create(author, event.copy(
                id = id, title = event.title.trim(), description = event.description.trim(), location = event.location.trim(),
                organizerId = author.id, organizerName = author.name, submittedByName = author.name,
                attendeeCount = 0, moderationStatus = EventModerationStatus.PENDING,
                rejectionReason = null, isFinished = false, isDeleted = false, submittedAtLabel = "ahora", photoUri = photo
            ))
        } catch (error: Exception) {
            if (photo != event.photoUri) removePhoto(photo)
            throw error
        }
        moderators().forEach { notify(it.id, "Publicación por revisar", created.title, created.id) }
        notify(userId, "Publicación enviada", "${created.title} está pendiente de revisión.", created.id)
        created
    }

    suspend fun update(userId: String, event: Event): Result<Event> = firebaseResult {
        val author = requireActiveUser(userId)
        validate(event)
        val photo = uploadPhoto(userId, event.photoUri)
        val change = try {
            mutate(event.id, author) { before, actor, _ ->
                val current = before.event
                require(!current.isDeleted && manages(actor, current)) { "No puedes editar esta publicación" }
                require(!current.isFinished && current.startsAt > System.currentTimeMillis()) { "El evento ya comenzó o finalizó" }
                val confirmed = before.registrations.count { it.status == EventRegistrationStatus.CONFIRMED }
                require(event.capacity >= confirmed) { "El cupo no puede ser menor que las asistencias confirmadas" }
                before.copy(event = current.copy(
                    title = event.title.trim(), description = event.description.trim(), location = event.location.trim(),
                    category = event.category, startsAt = event.startsAt, endsAt = event.endsAt, capacity = event.capacity,
                    price = event.price, photoUri = photo, moderationStatus = EventModerationStatus.PENDING,
                    rejectionReason = null, attendeeCount = confirmed, submittedAtLabel = "ahora"
                ))
            }
        } catch (error: Exception) {
            if (photo != event.photoUri) removePhoto(photo)
            throw error
        }
        val updated = requireNotNull(change.after).event
        if (change.before.event.photoUri != updated.photoUri) removePhoto(change.before.event.photoUri)
        moderators().forEach { notify(it.id, "Publicación actualizada por revisar", updated.title, updated.id) }
        notifyAttendees(change.before, "Evento actualizado", "${updated.title} cambió. Revisa las fechas; está pendiente de nueva aprobación.")
        updated
    }

    suspend fun moderate(userId: String, eventId: Int, status: EventModerationStatus, reason: String = ""): Result<Unit> = firebaseResult {
        val change = mutate(eventId, requireActiveUser(userId)) { before, actor, activeIds ->
            val event = before.event
            require(actor.role == UserRole.ADMINISTRATOR) { "Solo un moderador puede revisar publicaciones" }
            require(!event.isDeleted && event.moderationStatus == EventModerationStatus.PENDING) { "Esta publicación ya fue revisada o eliminada" }
            require(status != EventModerationStatus.PENDING) { "Selecciona aprobar o rechazar" }
            require(status != EventModerationStatus.REJECTED || reason.isNotBlank()) { "El motivo del rechazo es obligatorio" }
            if (status == EventModerationStatus.APPROVED) require(!event.isFinished && event.startsAt > System.currentTimeMillis()) { "No se puede aprobar un evento que ya comenzó" }
            val updated = before.copy(event = event.copy(moderationStatus = status, rejectionReason = reason.trim().takeIf { status == EventModerationStatus.REJECTED }))
            if (status == EventModerationStatus.APPROVED) promoteWaitlist(updated, activeIds)
            else normalized(updated.copy(registrations = emptyList()))
        }
        val event = change.before.event
        val approved = status == EventModerationStatus.APPROVED
        notify(event.organizerId, if (approved) "Publicación aprobada" else "Publicación rechazada",
            if (approved) "${event.title} ya es visible para la comunidad." else "${event.title}: ${reason.trim()}", eventId)
        if (approved) {
            communityRepository.following.value.filter { it.organizerId == event.organizerId }.forEach {
                if (activeUser(it.userId) != null) notify(it.userId, "Nuevo evento de alguien que sigues", event.title, eventId)
            }
            notifyAttendees(change.before, "Evento aprobado", "${event.title} está aprobado. Tu inscripción se conserva.")
            notifyPromoted(change)
        } else notifyAttendees(change.before, "Evento rechazado", "${event.title} fue rechazado. Tu inscripción se canceló. Motivo: ${reason.trim()}")
    }

    suspend fun delete(userId: String, eventId: Int): Result<Unit> = firebaseResult {
        removeEvent(eventId, requireActiveUser(userId), strictCleanup = false)
    }

    suspend fun finish(userId: String, eventId: Int): Result<Unit> = firebaseResult {
        val change = mutate(eventId, requireActiveUser(userId)) { before, actor, _ ->
            val event = before.event
            require(!event.isDeleted && manages(actor, event)) { "No puedes finalizar esta publicación" }
            require(event.moderationStatus == EventModerationStatus.APPROVED && !event.isFinished) { "Solo se puede finalizar un evento aprobado y activo" }
            require(event.startsAt <= System.currentTimeMillis()) { "El evento todavía no ha comenzado" }
            normalized(before.copy(event = event.copy(isFinished = true), registrations = before.registrations.filter { it.status == EventRegistrationStatus.CONFIRMED }))
        }
        notifyAttendees(change.before, "Evento finalizado", "${change.before.event.title} ha finalizado. Puedes compartir tu comentario y valoración.")
    }

    suspend fun register(userId: String, eventId: Int): Result<EventRegistrationStatus> = firebaseResult {
        val change = mutate(eventId, requireActiveUser(userId)) { before, actor, activeIds ->
            val event = before.event
            require(!event.isDeleted && actor.id != event.organizerId && event.moderationStatus == EventModerationStatus.APPROVED && !event.isFinished && event.startsAt > System.currentTimeMillis()) {
                "No puedes inscribirte: el evento debe estar aprobado y no haber comenzado"
            }
            if (before.registrations.any { it.userId == userId }) before else {
                // Se respeta la prioridad de quienes ya estaban en espera.
                val current = promoteWaitlist(before, activeIds)
                val confirmed = current.registrations.count { it.status == EventRegistrationStatus.CONFIRMED }
                val status = if (confirmed < event.capacity) EventRegistrationStatus.CONFIRMED else EventRegistrationStatus.WAITLIST
                val order = (before.registrations.maxOfOrNull { it.queueOrder } ?: 0L) + 1
                normalized(current.copy(registrations = current.registrations + EventRegistration(userId, eventId, status, System.currentTimeMillis(), order)))
            }
        }
        val status = requireNotNull(change.after).registrations.first { it.userId == userId }.status
        if (change.before.registrations.none { it.userId == userId }) {
            notify(userId, if (status == EventRegistrationStatus.CONFIRMED) "Asistencia confirmada" else "Estás en lista de espera",
                if (status == EventRegistrationStatus.CONFIRMED) "Tienes un cupo para ${change.before.event.title}." else "Te avisaremos si se libera un cupo para ${change.before.event.title}.", eventId)
        }
        notifyPromoted(change)
        status
    }

    suspend fun cancelRegistration(userId: String, eventId: Int): Result<Unit> = firebaseResult {
        val change = mutate(eventId, requireActiveUser(userId)) { before, _, activeIds ->
            val event = before.event
            require(!event.isDeleted && !event.isFinished && event.startsAt > System.currentTimeMillis()) { "No puedes cancelar después de iniciar el evento" }
            require(before.registrations.any { it.userId == userId }) { "No tienes una inscripción en este evento" }
            promoteWaitlist(before.copy(registrations = before.registrations.filterNot { it.userId == userId }), activeIds)
        }
        notifyPromoted(change)
        notify(userId, "Inscripción cancelada", "Cancelaste tu inscripción a ${change.before.event.title}.", eventId)
    }

    /** Consultas remotas para no dejar datos de otras sesiones fuera de la eliminación. */
    internal suspend fun removeAccountData(userId: String) {
        val actor = requireActiveUser(userId)
        eventRepository.ownedBy(userId).forEach { removeEvent(it.id, actor, strictCleanup = true) }
        eventRepository.registeredBy(userId).forEach { event ->
            val change = mutate(event.id, actor) { before, _, activeIds ->
                promoteWaitlist(before.copy(registrations = before.registrations.filterNot { it.userId == userId }), activeIds)
            }
            notifyPromoted(change)
        }
    }

    private suspend fun removeEvent(eventId: Int, actor: User, strictCleanup: Boolean) {
        val change = mutate(eventId, actor) { before, currentActor, _ ->
            require(manages(currentActor, before.event)) { "No puedes eliminar esta publicación" }
            null
        }
        val event = change.before.event
        if (!event.isDeleted) {
            notifyAttendees(change.before, "Publicación eliminada", "${event.title} fue eliminada; su inscripción quedó cancelada.")
            notify(event.organizerId, "Publicación eliminada", "${event.title} fue eliminada.")
        }
        val cleanup: suspend () -> Unit = {
            communityRepository.removeForEvent(eventId)
            eventRepository.purge(eventId, actor)
        }
        if (strictCleanup) cleanup() else try {
            cleanup()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            eventRepository.reportCleanupError("La publicación fue retirada, pero la limpieza de datos asociados quedó pendiente: ${error.toFirebaseMessage()}")
        }
        removePhoto(event.photoUri)
    }

    private suspend fun mutate(eventId: Int, actor: User, block: (EventRecord, User, Set<String>) -> EventRecord?): EventChange =
        eventRepository.mutate(eventId, actor, userRepository.users.value.filter { it.accountStatus == AccountStatus.ACTIVE }.map { it.id }.toSet(), block)

    private fun normalized(record: EventRecord): EventRecord = record.copy(event = record.event.copy(
        attendeeCount = record.registrations.count { it.status == EventRegistrationStatus.CONFIRMED }
    ))
    private fun promoteWaitlist(record: EventRecord, activeIds: Set<String>): EventRecord {
        val event = record.event
        if (event.isDeleted || event.isFinished || event.startsAt <= System.currentTimeMillis() || event.moderationStatus != EventModerationStatus.APPROVED) return normalized(record)
        val available = (event.capacity - record.registrations.count { it.status == EventRegistrationStatus.CONFIRMED }).coerceAtLeast(0)
        val promote = record.registrations.filter { it.status == EventRegistrationStatus.WAITLIST && it.userId in activeIds }
            .sortedWith(compareBy<EventRegistration> { it.queueOrder }.thenBy { it.registeredAt }).take(available).map { it.userId }.toSet()
        return normalized(record.copy(registrations = record.registrations.map { if (it.userId in promote) it.copy(status = EventRegistrationStatus.CONFIRMED) else it }))
    }
    private suspend fun notifyPromoted(change: EventChange) {
        val previousWaiting = change.before.registrations.filter { it.status == EventRegistrationStatus.WAITLIST }.map { it.userId }.toSet()
        change.after?.registrations.orEmpty().filter { it.status == EventRegistrationStatus.CONFIRMED && it.userId in previousWaiting }.forEach {
            notify(it.userId, "¡Se liberó un cupo!", "Tu asistencia a ${change.before.event.title} quedó confirmada desde la lista de espera.", change.before.event.id)
        }
    }
    private suspend fun notifyAttendees(record: EventRecord, title: String, message: String) {
        record.registrations.map { it.userId }.distinct().forEach { notify(it, title, message, record.event.id) }
    }
    private suspend fun notify(userId: String, title: String, message: String, eventId: Int? = null) = bestEffort {
        communityRepository.notify(userId, title, message, eventId)
    }
    private suspend fun uploadPhoto(userId: String, uri: String?): String? = when {
        uri == null -> null
        isRemoteImageUri(uri) -> uri
        uri.startsWith("content://") -> requireNotNull(mediaRepository) { "No se pudo cargar el servicio de imágenes" }.uploadImage(userId, uri, "events")
        else -> error("Selecciona una imagen válida")
    }
    private suspend fun removePhoto(uri: String?) {
        if (uri != null && isRemoteImageUri(uri) && mediaRepository != null) bestEffort { mediaRepository.deleteImage(uri) }
    }
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try { block() } catch (error: Exception) { if (error is CancellationException) throw error }
    }
    private fun activeUser(id: String) = userRepository.users.value.firstOrNull { it.id == id && it.accountStatus == AccountStatus.ACTIVE }
    private fun requireActiveUser(id: String): User = requireNotNull(activeUser(id)) { "La cuenta no está disponible" }
    private fun manages(actor: User, event: Event): Boolean = actor.accountStatus == AccountStatus.ACTIVE && (actor.id == event.organizerId || actor.role == UserRole.ADMINISTRATOR)
    private fun moderators() = userRepository.users.value.filter { it.role == UserRole.ADMINISTRATOR && it.accountStatus == AccountStatus.ACTIVE }
    private fun validate(event: Event) {
        require(event.title.isNotBlank()) { "El título es obligatorio" }
        require(event.description.isNotBlank()) { "La descripción es obligatoria" }
        require(event.location.isNotBlank()) { "La dirección es obligatoria" }
        require(event.capacity > 0) { "El cupo debe ser mayor que cero" }
        require(event.price >= 0) { "El precio no puede ser negativo" }
        require(event.startsAt > System.currentTimeMillis()) { "La fecha de inicio debe estar en el futuro" }
        require(event.endsAt > event.startsAt) { "La fecha de fin debe ser posterior al inicio" }
    }
}

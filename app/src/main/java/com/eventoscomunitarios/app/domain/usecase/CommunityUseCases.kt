package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.core.util.firebaseResult
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Reglas comunes; el repositorio resuelve escrituras y toggles atómicos en Firebase. */
class CommunityUseCases @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {
    val comments = communityRepository.comments
    val votes = communityRepository.votes
    val notifications = communityRepository.notifications
    val favorites = communityRepository.favorites
    val following = communityRepository.following
    val syncState = communityRepository.syncState

    suspend fun addComment(userId: String, eventId: Int, text: String, reviewId: String? = null, isContribution: Boolean = false): Result<EventComment> = firebaseResult {
        val user = requireActiveUser(userId)
        val event = requirePublishedEvent(eventId)
        require(if (isContribution) canContribute(userId, eventId) else canComment(userId, eventId)) { "Solo puedes comentar después de participar con inscripción confirmada y de finalizar el evento" }
        require(text.isNotBlank()) { "Escribe un comentario" }
        require(text.trim().length <= 1000) { "El comentario no puede superar 1000 caracteres" }
        val comment = EventComment(communityRepository.nextCommentId(), eventId, userId, user.name, text.trim(), System.currentTimeMillis(), isContribution, reviewId)
        communityRepository.saveComment(comment)
        // El comentario ya está guardado: un fallo del aviso no debe provocar que se publique de nuevo.
        if (event.organizerId != userId) notifySafely(event.organizerId, "Nuevo comentario", "${user.name} comentó en ${event.title}.", eventId)
        comment
    }

    suspend fun deleteComment(userId: String, commentId: Int): Result<Unit> = firebaseResult {
        val user = requireActiveUser(userId)
        val comment = requireNotNull(comments.value.firstOrNull { it.id == commentId }) { "Comentario no encontrado" }
        require(comment.userId == userId || user.role == UserRole.ADMINISTRATOR) { "Solo puedes eliminar tus comentarios" }
        communityRepository.deleteComment(commentId)
        if (comment.userId != userId) notifySafely(comment.userId, "Comentario retirado", "Un moderador retiró tu comentario.", comment.eventId)
    }

    suspend fun vote(userId: String, eventId: Int, value: Int): Result<Unit> = firebaseResult {
        requireActiveUser(userId)
        val event = requirePublishedEvent(eventId)
        require(event.organizerId != userId) { "No puedes votar tu propia publicación" }
        require(value == 1 || value == -1) { "El voto debe ser positivo o negativo" }
        communityRepository.toggleVote(userId, eventId, value)
    }

    suspend fun toggleFavorite(userId: String, eventId: Int): Result<Unit> = firebaseResult {
        requireActiveUser(userId)
        val event = requirePublishedEvent(eventId)
        communityRepository.toggleFavorite(userId, eventId, event.seriesId)
    }

    fun canComment(userId: String, eventId: Int): Boolean {
        val event = eventRepository.events.value.firstOrNull { it.id == eventId } ?: return false
        return userRepository.users.value.any { it.id == userId && it.accountStatus == AccountStatus.ACTIVE } &&
            !event.isDeleted && event.moderationStatus == EventModerationStatus.APPROVED && event.hasEnded &&
            eventRepository.registrations.value.any {
                it.userId == userId && it.eventId == eventId && it.status == EventRegistrationStatus.CONFIRMED
            }
    }

    fun canContribute(userId: String, eventId: Int): Boolean {
        val event = eventRepository.events.value.firstOrNull { it.id == eventId } ?: return false
        return userRepository.users.value.any { it.id == userId && it.accountStatus == AccountStatus.ACTIVE } &&
            !event.isDeleted && event.moderationStatus == EventModerationStatus.APPROVED && !event.hasEnded
    }

    suspend fun toggleFollow(userId: String, organizerId: String): Result<Unit> = firebaseResult {
        requireActiveUser(userId)
        requireActiveUser(organizerId)
        require(userId != organizerId) { "No puedes seguirte a ti mismo" }
        communityRepository.toggleFollow(userId, organizerId)
    }

    suspend fun markRead(userId: String, notificationId: Int): Result<Unit> = firebaseResult {
        requireActiveUser(userId)
        require(notifications.value.any { it.id == notificationId && it.userId == userId }) { "Notificación no encontrada" }
        communityRepository.markRead(userId, notificationId)
    }

    suspend fun markAllRead(userId: String): Result<Unit> = firebaseResult {
        requireActiveUser(userId)
        communityRepository.markRead(userId, null)
    }

    private suspend fun notifySafely(userId: String, title: String, message: String, eventId: Int) {
        try {
            communityRepository.notify(userId, title, message, eventId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // El aviso es secundario; la operación principal se conserva y no se repite.
        }
    }

    private fun requireActiveUser(userId: String): User = requireNotNull(userRepository.users.value.firstOrNull {
        it.id == userId && it.accountStatus == AccountStatus.ACTIVE
    }) { "La cuenta no está disponible" }

    private fun requirePublishedEvent(eventId: Int): Event {
        val event = requireNotNull(eventRepository.events.value.firstOrNull { it.id == eventId }) { "Publicación no encontrada" }
        require(!event.isDeleted) { "La publicación ya no está disponible" }
        require(event.moderationStatus == EventModerationStatus.APPROVED) { "La publicación todavía no está aprobada" }
        return event
    }
}

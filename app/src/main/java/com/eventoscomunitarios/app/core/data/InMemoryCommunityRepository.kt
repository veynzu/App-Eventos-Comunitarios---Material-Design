package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Implementación determinista para pruebas; producción usa FirebaseCommunityRepository. */
class InMemoryCommunityRepository @Inject constructor() : CommunityRepository {
    private val mutableComments = MutableStateFlow<List<EventComment>>(emptyList())
    private val mutableVotes = MutableStateFlow<List<EventVote>>(emptyList())
    private val mutableNotifications = MutableStateFlow<List<AppNotification>>(emptyList())
    private val mutableFavorites = MutableStateFlow<List<EventFavorite>>(emptyList())
    private val mutableFollowing = MutableStateFlow<List<UserFollow>>(emptyList())
    override val comments = mutableComments.asStateFlow()
    override val votes = mutableVotes.asStateFlow()
    override val notifications = mutableNotifications.asStateFlow()
    override val favorites = mutableFavorites.asStateFlow()
    override val following = mutableFollowing.asStateFlow()
    override val syncState = MutableStateFlow(RepositorySyncState()).asStateFlow()
    private var commentSequence = 1
    private var notificationSequence = 1

    override suspend fun nextCommentId(): Int = commentSequence++
    override suspend fun saveComment(comment: EventComment) {
        mutableComments.value = if (comments.value.any { it.id == comment.id }) comments.value.map {
            if (it.id == comment.id) comment else it
        } else comments.value + comment
    }
    override suspend fun deleteComment(commentId: Int) { mutableComments.value = comments.value.filterNot { it.id == commentId } }
    override suspend fun toggleVote(userId: String, eventId: Int, value: Int) {
        val previous = votes.value.firstOrNull { it.userId == userId && it.eventId == eventId }
        val remaining = votes.value.filterNot { it.userId == userId && it.eventId == eventId }
        mutableVotes.value = if (previous?.value == value) remaining else remaining + EventVote(eventId, userId, value)
    }
    override suspend fun notify(userId: String, title: String, message: String, eventId: Int?) {
        mutableNotifications.value = notifications.value + AppNotification(
            id = notificationSequence++, userId = userId, title = title, message = message,
            eventId = eventId, createdAt = System.currentTimeMillis()
        )
    }
    override suspend fun markRead(userId: String, notificationId: Int?) {
        mutableNotifications.value = notifications.value.map {
            if (it.userId == userId && (notificationId == null || it.id == notificationId)) it.copy(isRead = true) else it
        }
    }
    override suspend fun toggleFavorite(userId: String, eventId: Int) {
        val existed = favorites.value.any { it.userId == userId && it.eventId == eventId }
        val remaining = favorites.value.filterNot { it.userId == userId && it.eventId == eventId }
        mutableFavorites.value = if (existed) remaining else remaining + EventFavorite(userId, eventId)
    }
    override suspend fun toggleFollow(userId: String, organizerId: String) {
        val existed = following.value.any { it.userId == userId && it.organizerId == organizerId }
        val remaining = following.value.filterNot { it.userId == userId && it.organizerId == organizerId }
        mutableFollowing.value = if (existed) remaining else remaining + UserFollow(userId, organizerId)
    }
    override suspend fun removeForEvent(eventId: Int) {
        mutableComments.value = comments.value.filterNot { it.eventId == eventId }
        mutableVotes.value = votes.value.filterNot { it.eventId == eventId }
        mutableFavorites.value = favorites.value.filterNot { it.eventId == eventId }
        mutableNotifications.value = notifications.value.map { if (it.eventId == eventId) it.copy(eventId = null) else it }
    }
    override suspend fun removeForUser(userId: String) {
        mutableComments.value = comments.value.filterNot { it.userId == userId }
        mutableVotes.value = votes.value.filterNot { it.userId == userId }
        mutableNotifications.value = notifications.value.filterNot { it.userId == userId }
        mutableFavorites.value = favorites.value.filterNot { it.userId == userId }
        mutableFollowing.value = following.value.filterNot { it.userId == userId || it.organizerId == userId }
    }
}

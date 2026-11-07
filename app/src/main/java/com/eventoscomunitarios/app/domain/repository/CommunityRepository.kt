package com.eventoscomunitarios.app.domain.repository

import com.eventoscomunitarios.app.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface CommunityRepository {
    val comments: StateFlow<List<EventComment>>
    val votes: StateFlow<List<EventVote>>
    val notifications: StateFlow<List<AppNotification>>
    val favorites: StateFlow<List<EventFavorite>>
    val following: StateFlow<List<UserFollow>>
    val syncState: StateFlow<RepositorySyncState>
    suspend fun nextCommentId(): Int
    suspend fun saveComment(comment: EventComment)
    suspend fun deleteComment(commentId: Int)
    suspend fun toggleVote(userId: String, eventId: Int, value: Int)
    suspend fun notify(userId: String, title: String, message: String, eventId: Int? = null)
    suspend fun markRead(userId: String, notificationId: Int?)
    suspend fun toggleFavorite(userId: String, eventId: Int)
    suspend fun toggleFollow(userId: String, organizerId: String)
    suspend fun removeForEvent(eventId: Int)
    suspend fun removeForUser(userId: String)
}

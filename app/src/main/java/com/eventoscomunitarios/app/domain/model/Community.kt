package com.eventoscomunitarios.app.domain.model

data class EventComment(
    val id: Int,
    val eventId: Int,
    val userId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val isContribution: Boolean = false,
    val aiReviewId: String? = null
)

data class EventVote(val eventId: Int, val userId: String, val value: Int)
data class EventFavorite(val userId: String, val eventId: Int)
data class UserFollow(val userId: String, val organizerId: String)

data class AppNotification(
    val id: Int,
    val userId: String,
    val title: String,
    val message: String,
    val eventId: Int? = null,
    val createdAt: Long,
    val isRead: Boolean = false
)

/** Reputación: votos netos recibidos + cinco por evento finalizado, mínimo cero. */
data class UserStatistics(
    val publications: Int = 0,
    val approvedPublications: Int = 0,
    val finishedEvents: Int = 0,
    val confirmedAttendances: Int = 0,
    val waitlistedAttendances: Int = 0,
    val comments: Int = 0,
    val receivedVotes: Int = 0,
    val reputation: Int = 0
)

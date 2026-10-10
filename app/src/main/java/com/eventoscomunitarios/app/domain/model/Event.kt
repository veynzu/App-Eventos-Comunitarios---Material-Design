package com.eventoscomunitarios.app.domain.model


data class Event(
    val id: Int,
    val title: String,
    val dateLabel: String,
    val timeLabel: String,
    val location: String,
    val distanceKm: Double,
    val price: Int,
    val category: EventCategory,
    val attendeeCount: Int
) {
    val isFree: Boolean
        get() = price == 0
}

enum class EventCategory {
    SPORTS,
    CULTURE,
    COMMUNITY
}
